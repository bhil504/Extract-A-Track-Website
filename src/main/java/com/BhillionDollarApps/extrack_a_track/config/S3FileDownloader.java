package com.BhillionDollarApps.extrack_a_track.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

@Component
public class S3FileDownloader {

    private static final Logger logger = Logger.getLogger(S3FileDownloader.class.getName());

    @Autowired
    private S3Client s3Client;

    // Method to download a file from S3
    public void downloadFile(String bucketName, String s3Key, String localFilePath) throws IOException {
        try {
            // Log start of download
            logger.info("Starting download of file from S3. Bucket: " + bucketName + ", Key: " + s3Key);

            // Create the GetObjectRequest for the S3 object
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            // Define the path and ensure parent directories exist
            Path path = Paths.get(localFilePath);
            Files.createDirectories(path.getParent());

            // Check if the file already exists and delete it
            if (Files.exists(path)) {
                logger.warning("File already exists at path: " + localFilePath + ". Deleting it before download.");
                Files.delete(path);
            }

            // Download the file to the specified local path
            try (var s3Object = s3Client.getObject(getObjectRequest)) {
                Files.copy(s3Object, path, StandardCopyOption.REPLACE_EXISTING);
            }

            // Log successful download
            logger.info("File downloaded successfully from S3. Saved to: " + localFilePath);

        } catch (SdkClientException e) {
            // Log and throw client exception
            logger.severe("Error downloading file from S3: " + e.getMessage());
            throw new IOException("Error downloading file from S3", e);
        } catch (IOException e) {
            // Log and rethrow IO exception
            logger.severe("I/O Error during file download: " + e.getMessage());
            throw e;
        }
    }

    // Method to List the files in the S3
    public List<String> listFiles(String bucketName, String prefix) {
        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();
        ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

        return listResponse.contents().stream()
                .map(obj -> obj.key())
                .collect(Collectors.toList());
    }

    // Method to create a URL for the S3 key for metadata and downloads
    public String generatePresignedUrl(String bucketName, String s3Key) {
        try (S3Presigner presigner = S3Presigner.create()) {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            PresignedGetObjectRequest presignedGetObjectRequest = presigner.presignGetObject(
                    b -> b.getObjectRequest(getObjectRequest).signatureDuration(Duration.ofMinutes(60))
            );

            return presignedGetObjectRequest.url().toString();
        } catch (Exception e) {
            logger.severe("Error generating presigned URL for S3 key: " + s3Key + ". Exception: " + e.getMessage());
            throw new RuntimeException("Failed to generate presigned URL.", e);
        }
    }

    // Method to download a file from S3 as a resource
    public Resource downloadFileAsResource(String bucketName, String s3Key) throws IOException {
        String tempFilePath = System.getProperty("java.io.tmpdir") + "/" + s3Key.substring(s3Key.lastIndexOf("/") + 1);

        downloadFile(bucketName, s3Key, tempFilePath);

        Path path = Paths.get(tempFilePath);
        Resource resource = new UrlResource(path.toUri());
        if (resource.exists() && resource.isReadable()) {
            return resource;
        } else {
            throw new IOException("Could not read file: " + tempFilePath);
        }
    }

    // Method to download a file from S3 as Bytes
    public byte[] downloadFileAsBytes(String bucketName, String s3Key) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            ResponseBytes<GetObjectResponse> responseBytes = s3Client.getObjectAsBytes(getObjectRequest);
            return responseBytes.asByteArray();
        } catch (Exception e) {
            logger.severe("Error downloading file from S3 as bytes: " + e.getMessage());
            return null;
        }
    }
}
