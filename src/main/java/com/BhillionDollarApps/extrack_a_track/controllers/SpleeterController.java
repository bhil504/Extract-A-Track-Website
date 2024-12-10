package com.BhillionDollarApps.extrack_a_track.controllers;

import com.BhillionDollarApps.extrack_a_track.models.Tracks;
import com.BhillionDollarApps.extrack_a_track.repositories.TracksRepository;
import com.BhillionDollarApps.extrack_a_track.services.S3TempFileService;
import com.BhillionDollarApps.extrack_a_track.services.SpleeterService;
import com.BhillionDollarApps.extrack_a_track.config.S3FileDownloader;
import com.BhillionDollarApps.extrack_a_track.config.S3FileUploader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpSession;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CrossOrigin(origins = "https://extract-a-track.us", allowCredentials = "true")
@Controller
@RequestMapping("/spleeter")
public class SpleeterController {

    private static final Logger logger = Logger.getLogger(SpleeterController.class.getName());
    private final String virtualEnvPath = "/home/ubuntu/spleeter_env/bin/activate";
    private final String spleeterPath = "/home/ubuntu/spleeter_env/bin/spleeter";
    private final String permanentBucketName = "extract-a-trackbucket";
    private static final ConcurrentHashMap<Path, Integer> downloadCountMap = new ConcurrentHashMap<>();

    @Autowired
    private S3FileDownloader s3FileDownloader;

    @Autowired
    private S3FileUploader s3FileUploader;

    @Autowired
    private TracksRepository tracksRepository;

    @Autowired
    private SpleeterService spleeterService;

    @Autowired
    private HttpSession session;

    @Autowired
    private S3TempFileService s3TempFileService;

    @GetMapping("/form")
    public String displaySpleeterForm() {
        return "SpleeterForm";
    }

    /**
     * Updated exportTrackToSpleeter method to include user temp folder deletion.
     */
    @PostMapping("/{id}/export-spleeter")
    public String exportTrackToSpleeter(@PathVariable("id") Long trackId,
                                        @RequestParam("stems") int stems,
                                        RedirectAttributes redirectAttributes) {
        Long userId = (Long) session.getAttribute("userId");
        String trackStemsFolder = null; // Define outside try block for cleanup in finally block
        String userTempFolder = null;   // Define to ensure temp folder cleanup

        try {
            Tracks track = tracksRepository.findById(trackId)
                    .orElseThrow(() -> new RuntimeException("Track not found with ID: " + trackId));
            track.setStatus("PROCESSING");
            tracksRepository.save(track);

            String s3Key = track.getS3Key();
            if (s3Key == null || s3Key.isEmpty()) {
                throw new RuntimeException("No S3 key found for track ID: " + trackId);
            }

            // Define paths
            userTempFolder = s3TempFileService.getUserTempFolder(userId);
            String tempWavFilePath = userTempFolder + sanitizeTitle(track.getTitle()) + ".wav";
            String userStemsFolder = "/home/ubuntu/stems_output/" + userId + "/";
            trackStemsFolder = userStemsFolder + sanitizeTitle(track.getTitle()) + "/";

            // Ensure the folders exist and are cleaned up
            createAndCleanFolder(trackStemsFolder);
            createAndCleanFolder(userTempFolder);

            // Download the file
            logger.info("Downloading file from S3: " + s3Key);
            s3FileDownloader.downloadFile(permanentBucketName, s3Key, tempWavFilePath);

            // Run Spleeter command
            String spleeterCommand = String.format(
                    "source %s && %s separate -p spleeter:%dstems -o \"%s\" \"%s\"",
                    virtualEnvPath, spleeterPath, stems, trackStemsFolder, tempWavFilePath
            );

            logger.info("Executing command: " + spleeterCommand);
            Process process = executeCommand(spleeterCommand);
            if (process.waitFor() != 0) {
                throw new IOException("Spleeter process failed.");
            }

            handleSpleeterResponse(track, trackStemsFolder);

            redirectAttributes.addFlashAttribute("message", "Track processing has started.");
            return "redirect:/tracks/" + trackId;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing track ID: " + trackId, e);
            redirectAttributes.addFlashAttribute("error", "Failed to process the track.");
            return "redirect:/tracks/" + trackId;

        } finally {
            // Clean up the user temp and stems folder after processing
            try {
                if (trackStemsFolder != null) {
                    deleteLocalFolder(trackStemsFolder);
                    logger.info("Cleaned up stems folder: " + trackStemsFolder);
                }
                if (userTempFolder != null) {
                    deleteLocalFolder(userTempFolder);
                    logger.info("Cleaned up temp folder: " + userTempFolder);
                }
            } catch (IOException e) {
                logger.warning("Failed to clean up folders for user ID: " + userId + ". Exception: " + e.getMessage());
            }
        }
    }


    /**
     * Recursively deletes the specified folder and its contents.
     */
    private void deleteLocalFolder(String folderPath) throws IOException {
        Path folder = Paths.get(folderPath);
        if (Files.exists(folder)) {
            Files.walk(folder)
                    .sorted(Comparator.reverseOrder()) // Sort in reverse order to delete files before directories
                    .map(Path::toFile)
                    .forEach(file -> {
                        if (!file.delete()) {
                            logger.warning("Failed to delete file: " + file.getAbsolutePath());
                        }
                    });
        }
    }


    /**
     * Creates and cleans the specified folder.
     */
    private void createAndCleanFolder(String folderPath) throws IOException {
        File folder = new File(folderPath);
        if (folder.exists()) {
            // Delete existing contents
            for (File file : Objects.requireNonNull(folder.listFiles())) {
                if (!file.delete()) {
                    logger.warning("Failed to delete file: " + file.getAbsolutePath());
                }
            }
        } else if (!folder.mkdirs()) {
            throw new IOException("Failed to create folder: " + folderPath);
        }
    }


    @PostMapping("/uploadAndProcessTrack")
    @ResponseBody
    public ResponseEntity<?> uploadAndProcessTrack(
            @RequestParam("trackFile") MultipartFile trackFile,
            @RequestParam("stemCount") int stemCount) {
        Long userId = (Long) session.getAttribute("userId");
        String userTempFolder = "/home/ubuntu/temp/" + userId + "/";
        String userStemsFolder = "/home/ubuntu/stems_output/" + userId + "/";
        Path tempFilePath = null;

        try {
            // Create and clean user-specific folders
            createAndCleanFolder(userTempFolder);
            createAndCleanFolder(userStemsFolder);

            // Save uploaded file to the temp folder
            String sanitizedFilename = trackFile.getOriginalFilename().replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
            tempFilePath = Paths.get(userTempFolder, "spleeter-" + sanitizedFilename);
            trackFile.transferTo(tempFilePath.toFile());

            // Determine the Spleeter model
            String spleeterModel = (stemCount == 5) ? "spleeter:5stems" : "spleeter:2stems";

            // Build Spleeter command
            String spleeterCommand = String.format(
                    "source %s && %s separate -p %s -o \"%s\" \"%s\"",
                    virtualEnvPath, spleeterPath, spleeterModel, userStemsFolder, tempFilePath
            );

            logger.info("Executing Spleeter command: " + spleeterCommand);
            Process process = executeCommand(spleeterCommand);
            if (process.waitFor() != 0) {
                throw new IOException("Spleeter process failed.");
            }

            // Find and return the generated stems as download links
            List<String> downloadLinks = findStems(userStemsFolder);

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("stems", downloadLinks);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing track.", e);
            return ResponseEntity.status(500).body("Error processing track: " + e.getMessage());
        } finally {
            // Clean up user-specific folders
            try {
                if (tempFilePath != null && Files.exists(tempFilePath)) {
                    Files.delete(tempFilePath);
                    logger.info("Deleted temporary file: " + tempFilePath);
                }
                deleteLocalFolder(userTempFolder);
                deleteLocalFolder(userStemsFolder);
            } catch (IOException e) {
                logger.warning("Failed to clean up folders for user ID: " + userId + ". Exception: " + e.getMessage());
            }
        }
    }


    private List<String> findStems(String outputDir) throws IOException {
        List<String> downloadLinks = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Paths.get(outputDir))) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".wav"))
                 .forEach(path -> {
                     String downloadUrl = "/spleeter/download/" + path.getFileName().toString();
                     downloadLinks.add(downloadUrl);
                     logger.info("Found stem file: " + downloadUrl);
                 });
        }
        if (downloadLinks.isEmpty()) {
            logger.warning("No stems found in output directory: " + outputDir);
        }
        return downloadLinks;
    }
    
    /**
     * Recursively deletes all files in a directory.
     */
    private void deleteLocalStemsOutput(String directoryPath) {
        try {
            Path directory = Paths.get(directoryPath);
            if (!Files.exists(directory)) {
                logger.warning("Directory does not exist: " + directoryPath);
                return;
            }

            Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            logger.info("Deleted file: " + path.toString());
                        } catch (IOException e) {
                            logger.warning("Failed to delete file: " + path.toString());
                        }
                    });

            logger.info("Successfully cleaned directory: " + directoryPath);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error cleaning directory: " + directoryPath, e);
        }
    }
    
    private void handleSpleeterResponse(Tracks track, String outputDirectoryPath) {
        try {
            String s3StemsBasePath = track.getS3Key().substring(0, track.getS3Key().lastIndexOf("/original/")) + "/stems/";
            spleeterService.uploadSeparatedStemsToS3(outputDirectoryPath, track, s3StemsBasePath);
            track.setStatus("COMPLETED");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error uploading stems for track ID: " + track.getId(), e);
            track.setStatus("FAILED");
        } finally {
            tracksRepository.save(track);
        }
    }

    private String sanitizeTitle(String title) {
        return title.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
    }

    private Process executeCommand(String command) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", command);
        processBuilder.redirectErrorStream(true);
        return processBuilder.start();
    }
    
    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadStem(@PathVariable String filename) throws IOException {
        Path stemsOutputDir = Paths.get("/home/ubuntu/stems_output");

        // Search for the file within subdirectories of `stems_output`
        Optional<Path> filePathOpt = Files.walk(stemsOutputDir)
            .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().equals(filename))
            .findFirst();

        if (filePathOpt.isEmpty()) {
            System.out.println("File not found: " + filename);
            return ResponseEntity.notFound().build();
        }

        Path filePath = filePathOpt.get();
        Path parentDir = filePath.getParent();
        Resource resource = new UrlResource(filePath.toUri());

        // Track the download count for this folder
        downloadCountMap.merge(parentDir, 1, Integer::sum);

        // Count total files in the directory to determine if all files have been downloaded
        long totalFiles = Files.list(parentDir).count();

        // Check if all files in the folder have been downloaded
        if (downloadCountMap.getOrDefault(parentDir, 0) >= totalFiles) {
            // All files downloaded, delete the folder
            Files.walk(parentDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
            downloadCountMap.remove(parentDir); // Remove entry from the map
            System.out.println("Deleted folder: " + parentDir);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}
