package com.BhillionDollarApps.extrack_a_track.config;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import org.springframework.context.ApplicationListener;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.stereotype.Component;
import com.BhillionDollarApps.extrack_a_track.services.FileService;

import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class SessionDestroyedListener implements ApplicationListener<SessionDestroyedEvent> {

    private static final Logger logger = Logger.getLogger(SessionDestroyedListener.class.getName());
    private final FileService fileService;
    private final S3Client s3Client;
    private final String bucketName = "extract-a-trackbucket";

    public SessionDestroyedListener(FileService fileService, S3Client s3Client) {
        this.fileService = fileService;
        this.s3Client = s3Client;
    }

    @Override
    public void onApplicationEvent(SessionDestroyedEvent event) {
        // Assuming the user ID is stored in the session before it is destroyed
        HttpSession session = event.getSession();
        String userId = (String) session.getAttribute("userId");

        if (userId != null) {
            // Clean up local temporary files
            deleteLocalTempFiles(userId);

            // Define the S3 path for the user's temp folder
            String userTempFolderPath = "temp/" + userId + "/";

            // Perform S3 cleanup
            deleteS3Folder(userTempFolderPath);
        }
    }

    /**
     * Deletes the user's temporary local files.
     */
    private void deleteLocalTempFiles(String userId) {
        String localTempFolderPath = "/home/ubuntu/temp/" + userId;
        try {
            Path tempFolderPath = Paths.get(localTempFolderPath);
            if (Files.exists(tempFolderPath)) {
                Files.walk(tempFolderPath)
                        .sorted((a, b) -> b.compareTo(a)) // Delete files before deleting directories
                        .map(Path::toFile)
                        .forEach(file -> {
                            if (!file.delete()) {
                                logger.warning("Failed to delete file: " + file.getAbsolutePath());
                            } else {
                                logger.info("Deleted file: " + file.getAbsolutePath());
                            }
                        });
                logger.info("Deleted local temp folder: " + localTempFolderPath);
            } else {
                logger.info("Local temp folder does not exist: " + localTempFolderPath);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error deleting local temp folder: " + localTempFolderPath, e);
        }
    }

    /**
     * Deletes the specified folder in the S3 bucket.
     */
    private void deleteS3Folder(String folderPath) {
        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(folderPath)
                .build();

        ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

        List<ObjectIdentifier> objectsToDelete = listResponse.contents().stream()
                .map(s3Object -> ObjectIdentifier.builder().key(s3Object.key()).build())
                .collect(Collectors.toList());

        if (!objectsToDelete.isEmpty()) {
            DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(Delete.builder().objects(objectsToDelete).build())
                    .build();
            s3Client.deleteObjects(deleteRequest);
            logger.info("Deleted S3 folder: " + folderPath);
        } else {
            logger.info("No objects found in S3 folder: " + folderPath);
        }
    }
}
