package com.BhillionDollarApps.extrack_a_track.services;

import com.BhillionDollarApps.extrack_a_track.config.S3FileDownloader;
import com.BhillionDollarApps.extrack_a_track.config.S3FileUploader;
import com.BhillionDollarApps.extrack_a_track.models.Tracks;
import com.BhillionDollarApps.extrack_a_track.repositories.TracksRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class SpleeterService {

    @Autowired
    private TracksRepository tracksRepository;

    @Autowired
    private S3FileUploader s3FileUploader;

    @Autowired
    private S3FileDownloader s3FileDownloader;

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private S3TempFileService s3TempFileService;

    private static final Logger logger = Logger.getLogger(SpleeterService.class.getName());

    /**
     * Process the given track entity with Spleeter to separate it into stems.
     */
    public Map<String, Object> processWithSpleeter(Tracks track, int stemCount) throws Exception {
        String userTempFolder = s3TempFileService.getUserTempFolder((Long) request.getSession().getAttribute("userId"));
        String tempWavFilePath = userTempFolder + sanitizeTitle(track.getTitle()) + ".wav";
        String outputDirPath = userTempFolder + "stems/";

        try {
            // Download the track from S3
            logger.info("Downloading track from S3...");
            s3FileDownloader.downloadFile(s3TempFileService.getBucketName(), track.getS3Key(), tempWavFilePath);

            // Run Spleeter and process stems
            logger.info("Running Spleeter...");
            Map<String, Object> stemsData = processWithSpleeter(tempWavFilePath, outputDirPath, stemCount);
            logger.info("Spleeter processing completed successfully.");
            return stemsData;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing track with Spleeter.", e);
            throw new RuntimeException("Failed to process track with Spleeter.", e);

        } finally {
            // Clean up temporary files
            logger.info("Cleaning up temporary files...");
            s3TempFileService.deleteUserTempFiles((Long) request.getSession().getAttribute("userId"));
        }
    }

    /**
     * Process a track file with Spleeter.
     */
    private Map<String, Object> processWithSpleeter(String inputFilePath, String outputDirPath, int stemCount) throws Exception {
        String spleeterCommand = String.format(
                "spleeter separate -i \"%s\" -o \"%s\" -p spleeter:%dstems",
                inputFilePath, outputDirPath, stemCount
        );

        logger.info("Executing Spleeter command: " + spleeterCommand);

        Process process = new ProcessBuilder("bash", "-c", spleeterCommand).redirectErrorStream(true).start();
        logProcessOutput(process);

        if (process.waitFor() != 0) {
            throw new IOException("Spleeter process failed. Check logs for details.");
        }

        return readStemsData(Paths.get(outputDirPath));
    }

    /**
     * Log Spleeter process output.
     */
    private void logProcessOutput(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("Spleeter output: " + line);
            }
        }
    }

    /**
     * Read separated stem files into a map.
     */
    private Map<String, Object> readStemsData(Path outputDir) throws IOException {
        Map<String, Object> stemsData = new HashMap<>();
        try (Stream<Path> paths = Files.walk(outputDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".wav"))
                 .forEach(path -> {
                     try {
                         stemsData.put(path.getFileName().toString(), Files.readAllBytes(path));
                     } catch (IOException e) {
                         logger.log(Level.SEVERE, "Error reading stem file: " + path, e);
                     }
                 });
        }

        if (stemsData.isEmpty()) {
            throw new IOException("No stems found in output directory: " + outputDir.toString());
        }

        return stemsData;
    }

    /**
     * Upload separated stems to S3.
     */
    public void uploadSeparatedStemsToS3(String outputDirPath, Tracks track, String s3StemsBasePath) throws IOException {
        Path outputDir = Paths.get(outputDirPath);

        if (!Files.isDirectory(outputDir)) {
            throw new IOException("Invalid output directory: " + outputDirPath);
        }

        logger.info("Uploading stems to S3 at: " + s3StemsBasePath);
        try (Stream<Path> files = Files.walk(outputDir)) {
            files.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".wav"))
                 .forEach(path -> {
                     String stemName = path.getFileName().toString().replace(".wav", "").toLowerCase();
                     String s3StemKey = s3StemsBasePath + stemName + ".wav";

                     try {
                         // Handle the actual upload
                         s3FileUploader.uploadFile("extract-a-trackbucket", s3StemKey, path.toString());
                         updateTrackStemFields(track, stemName, s3StemKey);
                         logger.info("Uploaded stem: " + s3StemKey);
                     } catch (Exception e) {
                         logger.log(Level.SEVERE, "Failed to upload stem: " + path.getFileName(), e);
                         // Log the exception, but do not rethrow it, so the rest of the stems can still upload
                     }
                 });
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error while walking the output directory: " + outputDirPath, e);
            throw e; // Rethrow if walking the directory fails
        }

        // Save the updated track metadata
        tracksRepository.save(track);
        logger.info("Track metadata updated for track ID: " + track.getId());

        // Clean up local stems_output directory
        deleteLocalStemsOutput(outputDirPath);
    }

    /**
     * Update track stem fields in the database.
     */
    private void updateTrackStemFields(Tracks track, String stemName, String s3StemKey) {
        switch (stemName) {
            case "vocals":
                track.setVocals(s3StemKey);
                break;
            case "accompaniment":
                track.setAccompaniment(s3StemKey);
                break;
            case "piano":
                track.setPiano(s3StemKey);
                break;
            case "bass":
                track.setBass(s3StemKey);
                break;
            case "drums":
                track.setDrums(s3StemKey);
                break;
            default:
                logger.warning("Unrecognized stem name: " + stemName);
        }
    }

    /**
     * Sanitize string for safe file paths.
     */
    private String sanitizeTitle(String title) {
        return title.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
    }
    
    /**
     * Delete the local stems_output directory after uploading files to S3.
     */
    private void deleteLocalStemsOutput(String outputDirPath) {
        logger.info("Attempting to delete directory: " + outputDirPath);
        try {
            Files.walk(Paths.get(outputDirPath))
                 .sorted(Comparator.reverseOrder()) // Delete files before directories
                 .forEach(path -> {
                     try {
                         Files.delete(path);
                         logger.info("Deleted: " + path.toString());
                     } catch (IOException e) {
                         logger.warning("Failed to delete: " + path.toString());
                         logger.log(Level.SEVERE, "Error details: ", e);
                     }
                 });
            logger.info("Successfully deleted stems_output directory: " + outputDirPath);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error while deleting stems_output directory: " + outputDirPath, e);
        }
    }

    /**
     * Deletes the temporary files stored in the user's folder.
     */
    public void deleteUserTempFiles(Long userId) {
        if (userId == null) {
            logger.warning("User ID is null. Skipping temp file deletion.");
            return;
        }

        String userTempFolderPath = s3TempFileService.getUserTempFolder(userId);
        try {
            Path userTempFolder = Paths.get(userTempFolderPath);
            if (Files.exists(userTempFolder) && Files.isDirectory(userTempFolder)) {
                Files.walk(userTempFolder)
                     .sorted(Comparator.reverseOrder()) // Delete files before directories
                     .map(Path::toFile)
                     .forEach(file -> {
                         if (!file.delete()) {
                             logger.warning("Failed to delete file: " + file.getAbsolutePath());
                         } else {
                             logger.info("Deleted file: " + file.getAbsolutePath());
                         }
                     });
                logger.info("Deleted temporary files in folder: " + userTempFolderPath);
            } else {
                logger.info("User temp folder does not exist or is not a directory: " + userTempFolderPath);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error deleting temp files in user folder: " + userTempFolderPath, e);
        }
    }




}
