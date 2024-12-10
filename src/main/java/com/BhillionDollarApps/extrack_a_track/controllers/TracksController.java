package com.BhillionDollarApps.extrack_a_track.controllers;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.core.io.Resource;
import com.BhillionDollarApps.extrack_a_track.config.S3FileDownloader;
import com.BhillionDollarApps.extrack_a_track.config.S3FileUploader;
import com.BhillionDollarApps.extrack_a_track.models.Tracks;
import com.BhillionDollarApps.extrack_a_track.models.User;
import com.BhillionDollarApps.extrack_a_track.repositories.TracksRepository;
import com.BhillionDollarApps.extrack_a_track.services.FileService;
import com.BhillionDollarApps.extrack_a_track.services.TracksService;
import com.BhillionDollarApps.extrack_a_track.services.UserService;
import com.BhillionDollarApps.extrack_a_track.services.LibrosaService;
import java.util.Map;
import java.util.HashMap;
import org.springframework.http.MediaType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.S3Object;

@Controller
@RequestMapping("/tracks")
public class TracksController {

    private static final Logger logger = Logger.getLogger(TracksController.class.getName());
    private static final String BUCKET_NAME = "extract-a-trackbucket";
    

    @Autowired
    private TracksService tracksService;
    @Autowired
    private TracksRepository tracksRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private FileService fileService;
    @Autowired
    private LibrosaService librosaService;
    @Autowired
    private HttpSession session;
    @Autowired
    private S3FileDownloader S3FileDownloader;
    @Autowired
    private S3FileUploader S3FileUploader;

    @Autowired
    private S3Client s3Client; // Injected S3Client
    

//Display the form for creating a new track
    @GetMapping("/new")
    public String newTrackForm(Model model) {
        model.addAttribute("track", new Tracks());
        return "newTrack";
    }

//Route to upload track to S3 and store track metadata in the RDS
    @PostMapping("/upload-track")
    public ResponseEntity<Map<String, String>> uploadTrack(@Valid @ModelAttribute("track") Tracks track,
                                                           @RequestParam("file") MultipartFile file,
                                                           BindingResult result, Model model,
                                                           HttpSession session) {
        Map<String, String> response = new HashMap<>();
        long maxStorageLimit = 10L * 1024 * 1024 * 1024; // 10GB in bytes

        if (result.hasErrors() || file.isEmpty()) {
            response.put("status", "error");
            response.put("message", "Please fill all fields and upload a valid file.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // Get the logged-in user ID from the session
            Long userId = (Long) session.getAttribute("userId");
            logger.info("Retrieved user ID from session: " + userId); // Confirm user ID
            if (userId == null) {
                response.put("status", "error");
                response.put("message", "User not logged in. Please log in and try again.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // Retrieve the user from the database
            User user = userService.getUserByID(userId);
            if (user == null || !user.isSubscribed()) {
                response.put("status", "error");
                response.put("message", "You must be subscribed to upload tracks.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            // Calculate current storage usage
            long currentStorage = userService.calculateUserStorage(user.getEmail());
            long fileSize = file.getSize();
            if (currentStorage + fileSize > maxStorageLimit) {
                response.put("status", "error");
                response.put("message", "Storage limit exceeded. Please delete some files to free up space.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            // Proceed with uploading the track
            track.setUser(user); // Set the associated user
            track.setFileName(file.getOriginalFilename());
            track.setStatus("PROCESSING");

            // Save the track metadata to the database (initial save without S3 key)
            tracksRepository.save(track);

            // Define the S3 folder path for the track
            String trackFolderName = track.getFileName().substring(0, track.getFileName().lastIndexOf('.'));
            String s3FolderPath = "user-uploads/" + userId + "/" + trackFolderName + "/original/";

            // Full S3 key for the WAV file within the track folder
            String s3Key = s3FolderPath + track.getFileName();
            logger.info("S3 Key for upload: " + s3Key);

            // Upload the file to S3
            tracksService.uploadTrackToS3(s3Key, file);

            // Update track metadata with S3 details and mark as completed
            track.setS3Key(s3Key);
            track.setStatus("COMPLETED");

            // Save updated track metadata with the S3 key
            tracksRepository.save(track);

            logger.info("Track uploaded successfully for user: " + userId);

            // Return a JSON response indicating success and a redirect URL
            response.put("status", "success");
            response.put("redirectUrl", "/welcome");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error uploading track: " + e.getMessage(), e);
            response.put("status", "error");
            response.put("message", "An error occurred while uploading the track.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

//Method to Download the user-uploaded track as a WAV File from S3 using the S3 key stored in MySQL
    @GetMapping("/{id}/download-wav")
    public ResponseEntity<InputStreamResource> downloadOriginalWav(@PathVariable("id") Long trackId) {
        try {
            // Fetch the track details
            Tracks track = tracksService.findTrackById(trackId)
                    .orElseThrow(() -> new RuntimeException("Track not found with ID: " + trackId));

            // Download the WAV file from S3 as a stream
            InputStreamResource resource = tracksService.downloadWavFromS3AsStream(track);

            // Set headers to prompt download in the user's browser
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + track.getFileName() + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, "audio/wav");

            // Return the file data as a response
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
        } catch (Exception e) {
            logger.severe("Failed to download WAV file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

//Route to convert the user's uploaded WAV file to MP3 and upload the MP3 to S3 and update the track metadata
    @PostMapping("/{id}/convert-to-mp3-async")
    public String convertToMp3Async(@PathVariable("id") Long trackId, RedirectAttributes redirectAttributes) {
        String bucketName = "extract-a-trackbucket";

        try {
            // Fetch the track details
            Tracks track = tracksService.findTrackById(trackId).orElse(null);
            if (track == null) {
                logger.warning("Track with ID " + trackId + " not found.");
                redirectAttributes.addFlashAttribute("error", "Track not found.");
                return "redirect:/tracks/" + trackId;
            }

            // Define temp S3 and local paths
            String tempWavS3Key = "temp/" + track.getUser().getId() + "/" + track.getFileName(); // Temp WAV path in S3
            String tempLocalFilePath = "/home/ubuntu/temp/" + track.getUser().getId() + "/" + track.getFileName(); // Local temp WAV file

            // Correct the MP3 location
            String originalS3Key = track.getS3Key();
            String baseFolder = originalS3Key.substring(0, originalS3Key.lastIndexOf("/original/"));
            String mp3S3Key = baseFolder + "/mp3/" + track.getFileName().replace(".wav", ".mp3");

            // Download WAV file from S3
            logger.info("Downloading WAV file from S3: " + originalS3Key);
            S3FileDownloader.downloadFile(bucketName, originalS3Key, tempLocalFilePath);

            // Convert WAV to MP3 locally
            String tempMp3FilePath = tempLocalFilePath.replace(".wav", ".mp3");
            String ffmpegCommand = String.format("ffmpeg -i \"%s\" -codec:a libmp3lame -qscale:a 2 \"%s\"", tempLocalFilePath, tempMp3FilePath);
            executeCommand(ffmpegCommand);

            // Upload MP3 to S3
            logger.info("Uploading MP3 to S3: " + mp3S3Key);
            S3FileUploader.uploadFile(bucketName, mp3S3Key, tempMp3FilePath);

            // Update track metadata
            track.setMp3S3Key(mp3S3Key);
            tracksService.saveTrack(track);

            // Clean up local temp files
            deleteLocalTempFiles(tempLocalFilePath, tempMp3FilePath);

            redirectAttributes.addFlashAttribute("message", "MP3 conversion completed successfully.");
            return "redirect:/tracks/" + trackId;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during MP3 conversion for track ID " + trackId, e);
            redirectAttributes.addFlashAttribute("error", "Failed to convert track to MP3.");
            return "redirect:/tracks/" + trackId;
        }
    }

//Route to Download the MP3 file from S3 to user's local machine
	@GetMapping("/{id}/download-mp3")
	public ResponseEntity<Resource> downloadMp3File(@PathVariable("id") Long trackId) {
	    try {
	        // Fetch the track details from the database
	        Optional<Tracks> optionalTrack = tracksService.findTrackById(trackId);
	        if (optionalTrack.isEmpty()) {
	            return ResponseEntity.status(HttpStatus.NOT_FOUND)
	                                 .body(null);
	        }
	
	        Tracks track = optionalTrack.get();
	
	        String bucketName = "extract-a-trackbucket"; // Replace with your actual bucket name
	
	        // Get the S3 key for the MP3 file
	        String mp3S3Key = track.getMp3S3Key();
	        if (mp3S3Key == null || mp3S3Key.isEmpty()) {
	            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
	                                 .body(null);
	        }
	
	        // Download the MP3 file from S3 to a temporary location
	        String tempFilePath = System.getProperty("java.io.tmpdir") + "/" 
	                + track.getUser().getId() + "-" 
	                + track.getTitle().replaceAll("[^a-zA-Z0-9-_\\.]", "_") + ".mp3";
	
	        // Use S3FileDownloader or equivalent to fetch the file from S3
	        S3FileDownloader.downloadFile(bucketName, mp3S3Key, tempFilePath);
	
	        File mp3File = new File(tempFilePath);
	
	        // Ensure the file exists
	        if (!mp3File.exists()) {
	            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                                 .body(null);
	        }
	
	        // Prepare the resource for download
	        InputStreamResource resource = new InputStreamResource(new FileInputStream(mp3File));
	
	        // Create HTTP headers for the response
	        HttpHeaders headers = new HttpHeaders();
	        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + track.getTitle() + ".mp3\"");
	        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
	
	        // Return the file as a response entity
	        return ResponseEntity.ok()
	                             .headers(headers)
	                             .contentLength(mp3File.length())
	                             .body(resource);
	
	    } catch (Exception e) {
	        e.printStackTrace();
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                             .body(null);
	    }
	}

//Edit an existing track
	@GetMapping("/{id}/edit")
	public String editTrackForm(@PathVariable("id") Long id, Model model) {
	    Optional<Tracks> trackOpt = tracksService.findTrackById(id);
	    if (trackOpt.isPresent()) {
	        model.addAttribute("track", trackOpt.get());
	        return "editTrack";
	    }
	    return "redirect:/welcome";
	}

//Route to update the Audio file and/or the uploaded tracks metadata in the RDS
	@PostMapping("/update/{id}")
	public String updateTrack(@PathVariable("id") Long id,
                          @Valid @ModelAttribute("track") Tracks track,
                          @RequestParam("file") MultipartFile file,
                          BindingResult result, Model model, RedirectAttributes redirectAttributes, HttpSession session) {
    if (result.hasErrors() || file.isEmpty()) {
        redirectAttributes.addFlashAttribute("errorMessage", "Validation errors occurred or file is missing.");
        return "redirect:/tracks/" + id + "/edit";
    }

    try {
        // Step 1: Find the existing track and delete its S3 folder
        Optional<Tracks> trackOpt = tracksRepository.findById(id);
        if (trackOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Track not found.");
            return "redirect:/welcome";
        }

        Tracks existingTrack = trackOpt.get();
        String folderPrefix = existingTrack.getS3Key();
        if (folderPrefix != null && folderPrefix.contains("/original/")) {
            folderPrefix = folderPrefix.substring(0, folderPrefix.lastIndexOf("/original/")) + "/";
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid S3 path.");
            return "redirect:/welcome";
        }

        boolean deleted = fileService.deleteFolderFromS3(BUCKET_NAME, folderPrefix);
        if (!deleted) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete folder from S3.");
            return "redirect:/welcome";
        }

        // Step 2: Update track details and upload the new file to S3

        // Get the logged-in user ID from the session
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "User not logged in. Please log in and try again.");
            return "redirect:/tracks/" + id + "/edit";
        }

        // Update the existing track's metadata
        User user = userService.getUserByID(userId);
        existingTrack.setUser(user);
        existingTrack.setTitle(track.getTitle());
        existingTrack.setGenre(track.getGenre());
        existingTrack.setLyrics(track.getLyrics());
        existingTrack.setFileName(file.getOriginalFilename());
        existingTrack.setStatus("PROCESSING");

        // Define the S3 folder path for the new track
        String trackFolderName = existingTrack.getFileName().substring(0, existingTrack.getFileName().lastIndexOf('.'));
        String s3FolderPath = "user-uploads/" + userId + "/" + trackFolderName + "/original/";

        // Full S3 key for the WAV file within the track folder
        String s3Key = s3FolderPath + existingTrack.getFileName();

        // Upload the new file to S3
        String tempFilePath = tracksService.storeFileTemporarily(file);
        try {
            tracksService.uploadTrackToS3(BUCKET_NAME, s3Key, tempFilePath);
        } finally {
            tracksService.deleteTempFile(tempFilePath);
        }

        // Update the S3 key and status
        existingTrack.setS3Key(s3Key);
        existingTrack.setStatus("COMPLETED");

        // Save the updated track metadata with the same ID
        tracksRepository.save(existingTrack);

        redirectAttributes.addFlashAttribute("successMessage", "Track updated successfully!");
        return "redirect:/tracks/" + existingTrack.getId();

    } catch (Exception e) {
        logger.log(Level.SEVERE, "Error updating track: " + e.getMessage(), e);
        redirectAttributes.addFlashAttribute("errorMessage", "Failed to update track. Please try again.");
        return "redirect:/tracks/" + id + "/edit";
	    }
	}

//Route to display an individual track's details
	@GetMapping("/{id}")
	public String showTrack(@PathVariable("id") Long id, Model model, HttpSession session) {
	    Long userId = (Long) session.getAttribute("userId");
	    if (userId == null) {
	        return "redirect:/login"; // Redirect to login if the user is not logged in
	    }
	
	    Optional<Tracks> trackOpt = tracksService.findTrackById(id);
	    if (trackOpt.isPresent()) {
	        Tracks track = trackOpt.get();
	        model.addAttribute("track", track);
	        model.addAttribute("userId", userId); // Add userId to the model for ownership check
	        return "showTrack";
	    }
	
	    return "redirect:/welcome"; // Redirect if the track is not found
	}
  
//Route to delete the WAV file, MP3 file, and stems from Spleeter, if available and all metadata in the RDS
	@PostMapping("/delete/{id}")
	public String deleteTrack(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
	    try {
	        // Step 1: Fetch the track from the database
	        Optional<Tracks> trackOpt = tracksRepository.findById(id);
	        if (trackOpt.isEmpty()) {
	            redirectAttributes.addFlashAttribute("errorMessage", "Track not found.");
	            return "redirect:/welcome";
	        }

	        Tracks track = trackOpt.get();
	        String folderPrefix = track.getS3Key();

	        // Step 2: Validate and clean up the S3 folder path
	        if (folderPrefix != null && folderPrefix.contains("/original/")) {
	            folderPrefix = folderPrefix.substring(0, folderPrefix.lastIndexOf("/original/")) + "/";
	        } else {
	            redirectAttributes.addFlashAttribute("errorMessage", "Invalid S3 path.");
	            return "redirect:/welcome";
	        }

	        // Step 3: Delete the folder from S3
	        boolean deleted = fileService.deleteFolderFromS3(BUCKET_NAME, folderPrefix);
	        if (!deleted) {
	            redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete folder from S3.");
	            return "redirect:/welcome";
	        }

	        // Step 4: Delete the track from the database
	        tracksRepository.deleteById(id);
	        redirectAttributes.addFlashAttribute("successMessage", "Track deleted successfully.");

	    } catch (Exception e) {
	        redirectAttributes.addFlashAttribute("errorMessage", "An error occurred while deleting the track.");
	        logger.log(Level.SEVERE, "Error deleting track: " + e.getMessage(), e);
	    }

	    return "redirect:/welcome";
	}
    
//Route to download an individual stem that's been uploaded to S3
    @GetMapping("/{id}/download-stem")
    public ResponseEntity<InputStreamResource> downloadStem(@PathVariable Long id, @RequestParam String stem) {
    try {
        // Fetch the track details from the database
        Tracks track = tracksRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Track not found with ID: " + id));

        // Determine S3 key based on stem type
        String stemS3Key;
        switch (stem.toLowerCase()) {
            case "vocals": stemS3Key = track.getVocals(); break;
            case "accompaniment": stemS3Key = track.getAccompaniment(); break;
            case "bass": stemS3Key = track.getBass(); break;
            case "drums": stemS3Key = track.getDrums(); break;
            case "piano": stemS3Key = track.getPiano(); break;
            case "other": stemS3Key = track.getOther(); break;
            default: throw new IllegalArgumentException("Invalid stem type: " + stem);
        }

        // Check if the stem exists in the database
        if (stemS3Key == null) {
            throw new RuntimeException("Stem file not found for type: " + stem);
        }

        // Prepare S3 download request
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket("extract-a-trackbucket")  // Replace with your actual bucket name
                .key(stemS3Key)
                .build();

        // Use the injected s3Client instance to get the object
        ResponseInputStream<GetObjectResponse> s3ObjectStream = s3Client.getObject(getObjectRequest);

        // Create filename in the format "trackName-stem.wav"
        String fileName = track.getTitle().replaceAll("[^a-zA-Z0-9-_\\.]", "_") + "-" + stem + ".wav";

        // Return the stem file as a downloadable resource
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(s3ObjectStream));

    } catch (Exception e) {
        logger.log(Level.SEVERE, "Error downloading stem: " + e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}

//Route to send the user's uploaded track to Librosa for analysis and updates the track's metadata in the RDS
    @GetMapping("/downloadAndAnalyze/{trackId}")
    public String downloadAndAnalyze(@PathVariable Long trackId) {
        String bucketName = "extract-a-trackbucket";

        try {
            // Fetch track details
            Tracks track = tracksService.findTrackById(trackId)
                    .orElseThrow(() -> new RuntimeException("Track not found with ID: " + trackId));

            // Define temp S3 and local paths
            String tempS3Key = "temp/" + track.getUser().getId() + "/" + track.getFileName();
            String localTempFile = "/home/ubuntu/temp/" + track.getUser().getId() + "/" + track.getFileName();

            // Download WAV file from S3
            logger.info("Downloading WAV file from S3: " + track.getS3Key());
            S3FileDownloader.downloadFile(bucketName, track.getS3Key(), localTempFile);

            // Analyze with Librosa
            librosaService.analyzeTrackWithLibrosa(localTempFile, track);

            // Update track metadata
            tracksService.saveTrack(track);

            // Delete temp files
            deleteLocalTempFiles(localTempFile);

            logger.info("Analysis completed for track ID: " + trackId);
            return "redirect:/dashboard";

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during Librosa analysis for track ID: " + trackId, e);
            return "redirect:/errorPage";
        }
    }
    
    /**
     * Helper method to delete temporary files.
     */
    private void deleteLocalTempFiles(String... tempFilePaths) {
        for (String filePath : tempFilePaths) {
            try {
                File file = new File(filePath);
                if (file.exists() && file.delete()) {
                    logger.info("Deleted temp file: " + filePath);
                } else {
                    logger.warning("Failed to delete temp file: " + filePath);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error deleting temp file: " + filePath, e);
            }
        }

    }
    
    /**
     * Executes a shell command.
     * @param command The command to execute.
     * @throws IOException If an I/O error occurs.
     */
    private void executeCommand(String command) throws IOException, InterruptedException {
        logger.info("Executing command: " + command);

        // Create a ProcessBuilder to execute the command
        ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", command);
        processBuilder.redirectErrorStream(true); // Redirect error stream to standard output
        Process process = processBuilder.start();

        // Log the output of the process
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info(line); // Log each line of output
            }
        }

        // Wait for the process to complete and check its exit code
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Command failed with exit code: " + exitCode);
        }
    }

}
