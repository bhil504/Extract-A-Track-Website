package com.BhillionDollarApps.extrack_a_track.services;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class S3TempFileService {

    private final S3Client s3Client;

    private final String bucketName = "extract-a-track-tempbucket"; // Update with your bucket name

    public S3TempFileService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

// Method to return the bucket name
    public String getBucketName() {
        return bucketName;
    }

// Generate the folder path for the user's temp files
    public String getUserTempFolder(Long userId) {
        return "temp/" + userId + "/";
    }

// Delete all files in the user's temp folder on S3
    public void deleteUserTempFiles(Long userId) {
        String folderPrefix = getUserTempFolder(userId);

// List objects in the user's temp folder
        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(folderPrefix)
                .build();

        ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

        // Collect all keys to delete
        List<ObjectIdentifier> objectsToDelete = listResponse.contents().stream()
                .map(s3Object -> ObjectIdentifier.builder().key(s3Object.key()).build())
                .collect(Collectors.toList());

        if (!objectsToDelete.isEmpty()) {
            DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(Delete.builder().objects(objectsToDelete).build())
                    .build();

            s3Client.deleteObjects(deleteRequest);
        }
    }

// Upload a temporary file to the user's S3 temp folder
    public String uploadTempFile(Long userId, String tempFilePath, String fileName) {
        String folderPath = getUserTempFolder(userId);
        String s3Key = folderPath + fileName;

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        // Use Paths.get() to convert the tempFilePath string into a Path object
        s3Client.putObject(putRequest, Paths.get(tempFilePath));

        return s3Key;
    }
    
// Generate a pre-signed URL for an S3 object
    public String generatePresignedUrl(String s3Key) {
        try (S3Presigner presigner = S3Presigner.create()) {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            PresignedGetObjectRequest presignedGetObjectRequest = presigner.presignGetObject(
                    b -> b.getObjectRequest(getObjectRequest)
                            .signatureDuration(Duration.ofMinutes(5)) // URL valid for 5 minutes
            );

            return presignedGetObjectRequest.url().toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL for key: " + s3Key, e);
        }
    }
    
    
}
