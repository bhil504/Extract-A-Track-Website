package com.BhillionDollarApps.extrack_a_track.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.util.logging.Level;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@EnableAsync
public class FileService {

    private static final Logger logger = Logger.getLogger(FileService.class.getName());

    private final S3Client s3Client;

    @Autowired
    public FileService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

//Helper method to ensure the file download from S3 during the Convert to MP3 process is a valid WAV file
    @Async
    public CompletableFuture<byte[]> convertWavToMp3Async(byte[] wavData) {
        // Validate input
        if (wavData == null || wavData.length == 0) {
            logger.warning("Invalid WAV data provided for MP3 conversion.");
            throw new IllegalArgumentException("WAV data cannot be null or empty.");
        }

        try {
            logger.info("Starting WAV to MP3 conversion asynchronously.");
            byte[] mp3Data = convertWavToMp3UsingFFmpeg(wavData);
            logger.info("WAV to MP3 conversion completed successfully.");
            return CompletableFuture.completedFuture(mp3Data);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during WAV to MP3 conversion: " + e.getMessage(), e);
            throw new RuntimeException("MP3 conversion failed.", e);
        }
    }

//Method to convert the WAV file to MP3 in bytes
    private byte[] convertWavToMp3UsingFFmpeg(byte[] wavData) throws IOException, InterruptedException {
        // Create temporary files for WAV and MP3
        File tempWavFile = File.createTempFile("temp_audio", ".wav");
        File tempMp3File = File.createTempFile("temp_audio_converted", ".mp3");

        try {
            // Write WAV data to temporary WAV file
            Files.write(tempWavFile.toPath(), wavData);

            // Construct FFmpeg command
            String[] command = {
                    "ffmpeg", "-y", "-i", tempWavFile.getAbsolutePath(),
                    "-codec:a", "libmp3lame", "-b:a", "192k", tempMp3File.getAbsolutePath()
            };

            // Execute the command
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            // Capture the output and wait for the process to complete
            int exitCode = process.waitFor();

            // Log errors if FFmpeg fails
            if (exitCode != 0) {
                throw new RuntimeException("FFmpeg conversion failed. Exit code: " + exitCode);
            }

            // Read the MP3 file into a byte array
            byte[] mp3Data = Files.readAllBytes(tempMp3File.toPath());

            // Return the converted MP3 data
            return mp3Data;

        } finally {
            // Clean up temporary files
            if (tempWavFile.exists() && !tempWavFile.delete()) {
                logger.warning("Failed to delete temporary WAV file: " + tempWavFile.getAbsolutePath());
            }
            if (tempMp3File.exists() && !tempMp3File.delete()) {
                logger.warning("Failed to delete temporary MP3 file: " + tempMp3File.getAbsolutePath());
            }
        }
    }

//Method to upload the MP3 file directly and correctly to S3
    public String uploadFileToS3(Path filePath, String bucketName, String s3Key) {
        // Validate inputs
        if (filePath == null || !Files.exists(filePath)) {
            throw new IllegalArgumentException("File path must not be null and must exist.");
        }
        if (bucketName == null || bucketName.trim().isEmpty()) {
            throw new IllegalArgumentException("Bucket name must not be null or empty.");
        }
        if (s3Key == null || s3Key.trim().isEmpty()) {
            throw new IllegalArgumentException("S3 key must not be null or empty.");
        }

        try {
            // Prepare and execute the S3 upload
            logger.info("Uploading file to S3. Bucket: " + bucketName + ", Key: " + s3Key);
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(s3Key)
                            .build(),
                    filePath);

            logger.info("File successfully uploaded to S3. Key: " + s3Key);
            return s3Key;

        } catch (Exception e) {
            // Log detailed exception and rethrow
            logger.log(Level.SEVERE, "Error uploading file to S3. Bucket: " + bucketName + ", Key: " + s3Key, e);
            throw new RuntimeException("Failed to upload file to S3. Key: " + s3Key, e);
        }
    }

//Method to identify and delete a folder from S3
    public boolean deleteFolderFromS3(String bucketName, String folderPrefix) {
    // Validate inputs
    if (bucketName == null || bucketName.trim().isEmpty()) {
        throw new IllegalArgumentException("Bucket name cannot be null or empty.");
    }
    if (folderPrefix == null || folderPrefix.trim().isEmpty()) {
        throw new IllegalArgumentException("Folder prefix cannot be null or empty.");
    }

    try {
        logger.info("Starting deletion of folder in S3. Bucket: " + bucketName + ", Prefix: " + folderPrefix);

        ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(folderPrefix)
                .build();

        ListObjectsV2Response listObjectsResponse;
        do {
            listObjectsResponse = s3Client.listObjectsV2(listObjectsRequest);

            List<ObjectIdentifier> keysToDelete = listObjectsResponse.contents().stream()
                    .map(S3Object::key)
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .collect(Collectors.toList());

            if (!keysToDelete.isEmpty()) {
                DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                        .bucket(bucketName)
                        .delete(Delete.builder().objects(keysToDelete).build())
                        .build();
                s3Client.deleteObjects(deleteRequest);
                logger.info("Deleted " + keysToDelete.size() + " objects from S3 folder: " + folderPrefix);
            }

            listObjectsRequest = listObjectsRequest.toBuilder()
                    .continuationToken(listObjectsResponse.nextContinuationToken())
                    .build();

        } while (listObjectsResponse.isTruncated());

        logger.info("Completed deletion of folder in S3. Prefix: " + folderPrefix);
        return true;

    } catch (Exception e) {
        logger.log(Level.SEVERE, "Error deleting folder from S3. Bucket: " + bucketName + ", Prefix: " + folderPrefix, e);
        return false;
    }
}

    public void deleteTempFolderForUser(String userId) {
        String tempFolderPath = "/home/ubuntu/temp/" + userId;
        File tempFolder = new File(tempFolderPath);

        if (tempFolder.exists() && tempFolder.isDirectory()) {
            for (File file : tempFolder.listFiles()) {
                file.delete();
            }
            tempFolder.delete();
        }
    }

}
