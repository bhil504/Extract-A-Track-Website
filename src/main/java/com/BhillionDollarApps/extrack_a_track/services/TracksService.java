package com.BhillionDollarApps.extrack_a_track.services;

import com.BhillionDollarApps.extrack_a_track.config.S3FileDownloader;
import com.BhillionDollarApps.extrack_a_track.models.Tracks;
import com.BhillionDollarApps.extrack_a_track.repositories.TracksRepository;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;



@Service
public class TracksService {

    private static final Logger logger = Logger.getLogger(TracksService.class.getName());

    @Autowired
    private TracksRepository tracksRepository;
    @Autowired
    private FileService fileService;
    @Autowired
    private S3Client s3Client; // Use S3Client for AWS SDK v2
    @Autowired
    private S3FileDownloader s3FileDownloader;


    private final String BUCKET_NAME = "extract-a-trackbucket"; // Set your bucket name

// Method to upload a file to S3
    public void uploadTrackToS3(String s3Key, MultipartFile file) {
        // Validate input
        if (s3Key == null || s3Key.trim().isEmpty()) {
            throw new IllegalArgumentException("S3 key cannot be null or empty.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be null or empty.");
        }
        if (!"audio/wav".equalsIgnoreCase(file.getContentType())) {
            throw new IllegalArgumentException("Only WAV files are allowed for upload.");
        }

        try {
            // Prepare the S3 upload request
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .build();

            // Perform the upload
            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

            // Log the successful upload
            logger.info("File uploaded successfully to S3. Key: " + s3Key);

        } catch (Exception e) {
            // Log the error and rethrow as a runtime exception
            logger.log(Level.SEVERE, "Error uploading file to S3. Key: " + s3Key, e);
            throw new RuntimeException("Failed to upload file to S3.", e);
        }
    }
    
// Method to upload a file to S3
    public void uploadTrackToS3(String bucketName, String s3Key, String filePath) {
    	try {
    		File file = new File(filePath);
    		s3Client.putObject(PutObjectRequest.builder()
    				.bucket(bucketName)
    				.key(s3Key)
    				.build(),
    				RequestBody.fromFile(file));
    		
    		System.out.println("File uploaded to S3 with key: " + s3Key);
    	} catch (Exception e) {
    		System.err.println("Failed to upload file to S3: " + e.getMessage());
    		throw new RuntimeException("S3 upload failed", e);
    	}
    }

// Save a new track with metadata
    public Tracks saveTrack(Tracks track) {
        // Validate track fields
        if (track.getTitle() == null || track.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Track title cannot be empty.");
        }
        if (track.getGenre() == null || track.getGenre().trim().isEmpty()) {
            throw new IllegalArgumentException("Track genre cannot be empty.");
        }

        // Save the track
        Tracks savedTrack = tracksRepository.save(track);
        logger.info("Track saved successfully with ID: " + savedTrack.getId() + ", Title: " + savedTrack.getTitle());
        return savedTrack;
    }

// Find a track by its ID
    public Optional<Tracks> findTrackById(Long id) {
        return tracksRepository.findById(id);
    }

// Find all tracks for a specific user
    public List<Tracks> findTracksByUserId(Long userId) {
        return tracksRepository.findByUserId(userId);
    }
    
// Method to download WAV from S3 and return it as a byte array
    public byte[] downloadWavFromS3(Long trackId) {
        // Validate inputs
        if (trackId == null) {
            throw new IllegalArgumentException("Track ID cannot be null.");
        }

        try {
            // Fetch the track from the database
            Tracks track = tracksRepository.findById(trackId)
                    .orElseThrow(() -> new RuntimeException("Track not found with ID: " + trackId));

            String s3Key = track.getS3Key();
            if (s3Key == null || s3Key.trim().isEmpty()) {
                throw new RuntimeException("Invalid S3 key for track ID: " + trackId);
            }

            // Prepare the S3 download request
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(s3Key)
                    .build();

            logger.info("Downloading file from S3. Key: " + s3Key);

            // Stream the S3 object
            try (ResponseInputStream<GetObjectResponse> s3ObjectStream = s3Client.getObject(request)) {
                // Convert the stream to a byte array and return
                return IOUtils.toByteArray(s3ObjectStream); // Requires Apache Commons IO
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error downloading file from S3 for track ID: " + trackId, e);
            throw new RuntimeException("Failed to download file from S3 for track ID: " + trackId, e);
        }
    }
    
// Method to download WAV from S3 and return it as an InputStreamResource
    public InputStreamResource downloadWavFromS3AsStream(Tracks track) {
        try {
            String s3Key = track.getS3Key();
            if (s3Key == null || s3Key.trim().isEmpty()) {
                throw new RuntimeException("Invalid S3 key for track ID: " + track.getId());
            }

            // Prepare the S3 download request
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(s3Key)
                    .build();

            logger.info("Downloading file from S3. Key: " + s3Key);

            // Stream the S3 object
            ResponseInputStream<GetObjectResponse> s3ObjectStream = s3Client.getObject(request);

            // Wrap the stream in InputStreamResource for returning to the client
            return new InputStreamResource(s3ObjectStream);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error downloading file from S3 for track ID: " + track.getId(), e);
            throw new RuntimeException("Failed to download file from S3 for track ID: " + track.getId(), e);
        }
    }

// Method to delete a track from S3
    public void deleteTrackFromS3(String s3Key) {
        // Validate input
        if (s3Key == null || s3Key.trim().isEmpty()) {
            throw new IllegalArgumentException("S3 key cannot be null or empty.");
        }

        try {
            // Delete the file from S3
            logger.info("Attempting to delete file from S3. Key: " + s3Key + ", Bucket: " + BUCKET_NAME);
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(s3Key)
                    .build());
            logger.info("File successfully deleted from S3. Key: " + s3Key);

        } catch (Exception e) {
            // Log detailed error and rethrow
            logger.log(Level.SEVERE, "Error deleting file from S3. Key: " + s3Key + ", Bucket: " + BUCKET_NAME, e);
            throw new RuntimeException("Failed to delete file from S3. Key: " + s3Key, e);
        }
    }

// Method to store a file temporarily
    public String storeFileTemporarily(MultipartFile file) {
        // Validate input
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be null or empty.");
        }

        try {
            // Create a temporary file with a unique name
            String tempDir = System.getProperty("java.io.tmpdir");
            String sanitizedFileName = file.getOriginalFilename().replaceAll("[^a-zA-Z0-9-_\\.]", "_");
            File tempFile = new File(tempDir, "temp-" + sanitizedFileName);

            // Write the file's content to the temporary file
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(file.getBytes());
            }

            logger.info("Temporary file created at: " + tempFile.getAbsolutePath());
            return tempFile.getAbsolutePath();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error storing file temporarily: " + e.getMessage(), e);
            throw new RuntimeException("Failed to store file temporarily.", e);
        }
    }

// Method to delete a temporary file
    public void deleteTempFile(String tempFilePath) {
        // Validate input
        if (tempFilePath == null || tempFilePath.trim().isEmpty()) {
            logger.warning("Invalid temporary file path: null or empty.");
            return;
        }

        File tempFile = new File(tempFilePath);
        try {
            if (tempFile.exists()) {
                if (tempFile.delete()) {
                    logger.info("Temporary file deleted successfully: " + tempFilePath);
                } else {
                    logger.warning("Failed to delete temporary file: " + tempFilePath);
                }
            } else {
                logger.info("Temporary file does not exist: " + tempFilePath);
            }
        } catch (SecurityException e) {
            logger.log(Level.SEVERE, "Security exception while deleting temporary file: " + tempFilePath, e);
        }
    }

// Method to upload a track to S3 from a file path
    public void uploadTrackToS3(String s3Key, String filePath) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(s3Key)
                    .build(), RequestBody.fromFile(Paths.get(filePath)));
            logger.info("File uploaded to S3 with key: " + s3Key);
        } catch (Exception e) {
            throw new RuntimeException("Error uploading file to S3 with key: " + s3Key, e);
        }
    }
    
// Method to delete a folder from S3
    public void deleteS3Folder(String bucketName, String folderPrefix) {
    // Validate inputs
    if (bucketName == null || bucketName.trim().isEmpty()) {
        throw new IllegalArgumentException("Bucket name cannot be null or empty.");
    }
    if (folderPrefix == null || folderPrefix.trim().isEmpty()) {
        throw new IllegalArgumentException("Folder prefix cannot be null or empty.");
    }

    try {
        logger.info("Starting folder deletion. Bucket: " + bucketName + ", Prefix: " + folderPrefix);

        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(folderPrefix)
                .build();

        ListObjectsV2Response response;

        // Handle pagination for large folders
        do {
            response = s3Client.listObjectsV2(listRequest);

            for (S3Object s3Object : response.contents()) {
                try {
                    s3Client.deleteObject(DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(s3Object.key())
                            .build());
                    logger.info("Deleted file from S3: " + s3Object.key());
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to delete file: " + s3Object.key(), e);
                }
            }

            listRequest = listRequest.toBuilder()
                    .continuationToken(response.nextContinuationToken())
                    .build();

        } while (response.isTruncated());

        logger.info("Folder deletion completed. Prefix: " + folderPrefix);

    } catch (Exception e) {
        logger.log(Level.SEVERE, "Error deleting folder from S3. Bucket: " + bucketName + ", Prefix: " + folderPrefix, e);
        throw new RuntimeException("Failed to delete folder from S3. Prefix: " + folderPrefix, e);
    }
}

// Method to find and delete a track based on the Track ID
    public void deleteTrackById(Long id) {
        // Validate input
        if (id == null) {
            throw new IllegalArgumentException("Track ID cannot be null.");
        }

        try {
            // Fetch the track from the database
            Optional<Tracks> trackOpt = tracksRepository.findById(id);
            if (trackOpt.isPresent()) {
                Tracks track = trackOpt.get();

                // Construct the folder prefix for S3
                String folderPrefix = "user-uploads/" + track.getUser().getId() + "/" 
                        + track.getTitle().replaceAll("[^a-zA-Z0-9-_\\.]", "_") + "/";
                logger.info("Deleting S3 folder with prefix: " + folderPrefix);

                // Delete the S3 folder
                deleteS3Folder(BUCKET_NAME, folderPrefix);

                // Delete the track record from the database
                tracksRepository.deleteById(id);
                logger.info("Track deleted successfully with ID: " + id);
            } else {
                logger.warning("Track with ID " + id + " not found in the database.");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error deleting track with ID: " + id, e);
            throw new RuntimeException("Failed to delete track with ID: " + id, e);
        }
    }


    // Additional methods for retrieving and managing tracks can go here
}
