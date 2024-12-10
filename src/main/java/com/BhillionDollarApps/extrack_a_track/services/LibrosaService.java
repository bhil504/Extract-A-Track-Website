package com.BhillionDollarApps.extrack_a_track.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.logging.Level;
import com.BhillionDollarApps.extrack_a_track.config.S3FileDownloader;
import com.BhillionDollarApps.extrack_a_track.models.Tracks;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LibrosaService {
    
    private static final Logger logger = Logger.getLogger(LibrosaService.class.getName());
    private final String virtualEnvPath = "/home/ubuntu/librosa_env/bin/activate";
    private final String pythonPath = "/home/ubuntu/librosa_env/bin/python3";
    private final String scriptPath = "/home/ubuntu/librosa_env/librosa_api.py";

    @Autowired
    private S3FileDownloader s3FileDownloader;

    private final ObjectMapper objectMapper = new ObjectMapper();

//Method to send a file through Librosa for processing and updates the tracks metadata
    public Tracks analyzeTrackWithLibrosa(String filePath, Tracks track) {
        // Validate inputs
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty.");
        }
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("File does not exist or is not a valid file: " + filePath);
        }

        try {
            logger.info("Starting analysis with Librosa on file: " + filePath);

            // Build and execute the Python script command
            ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c",
                "source " + virtualEnvPath + " && " + pythonPath + " " + scriptPath + " " + filePath);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Capture the script's output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.severe("Librosa script failed with exit code: " + exitCode + ". Output: " + output);
                throw new RuntimeException("Python script execution failed. Exit code: " + exitCode);
            }

            logger.info("Librosa script executed successfully. Processing results...");

            // Parse and process the analysis results
            Map<String, Object> analysisResults = objectMapper.readValue(output.toString(), Map.class);
            if (analysisResults.containsKey("error")) {
                String error = (String) analysisResults.get("error");
                logger.severe("Error reported by Python script: " + error);
                throw new RuntimeException("Error from Python script: " + error);
            }

            // Update the track object with analysis results
            track.setTempo(((Double) analysisResults.get("tempo")).floatValue());
            track.setSpectralCentroid(((Double) analysisResults.get("spectral_centroid")).floatValue());
            track.setRms(((Double) analysisResults.get("rms")).floatValue());
            track.setSongKey(analysisResults.getOrDefault("key", "Unknown").toString());

            track.setBeats(objectMapper.writeValueAsString(analysisResults.getOrDefault("beats", new int[0])));
            track.setMelody(objectMapper.writeValueAsString(analysisResults.getOrDefault("melody", new float[0])));
            track.setMfcc(objectMapper.writeValueAsString(analysisResults.getOrDefault("mfcc", new float[0])));
            track.setSpectralFeatures(objectMapper.writeValueAsString(analysisResults.getOrDefault("spectral_features", new HashMap<>())));

            logger.info("Analysis completed successfully for file: " + filePath);
            return track;

        } catch (IOException e) {
            logger.log(Level.SEVERE, "I/O error during Librosa analysis: " + e.getMessage(), e);
            throw new RuntimeException("I/O error during Librosa analysis.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(Level.SEVERE, "Librosa analysis interrupted: " + e.getMessage(), e);
            throw new RuntimeException("Librosa analysis was interrupted.", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error during Librosa analysis: " + e.getMessage(), e);
            throw new RuntimeException("Unexpected error during Librosa analysis.", e);
        }
    }

//Helper method to format the Beats from Librosa processing in a readable way
    public String formatBeats(List<Integer> beatsList) {
        // Validate input
        if (beatsList == null) {
            logger.warning("Beats list is null. Returning empty beats information.");
            return "Beats: 0 (No beats available)";
        }

        try {
            int totalBeats = beatsList.size();
            String firstFiveBeats = beatsList.subList(0, Math.min(5, totalBeats)).toString();
            logger.info("Formatted beats: Total = " + totalBeats + ", First 5 = " + firstFiveBeats);
            return "Beats: " + totalBeats + " (First 5: " + firstFiveBeats + ")";
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error formatting beats list: " + e.getMessage(), e);
            return "Error formatting beats information.";
        }
    }

//Helper method for the Melody from Librosa processing to be readable
    public String formatMelody(List<Float> melodyList) {
        // Validate input
        if (melodyList == null) {
            logger.warning("Melody list is null. Returning default melody information.");
            return "Melody (average): 0.000";
        }

        try {
            double averageMelody = melodyList.stream()
                                             .mapToDouble(Float::doubleValue)
                                             .average()
                                             .orElse(0.0);
            logger.info("Calculated average melody: " + averageMelody);
            return "Melody (average): " + String.format("%.3f", averageMelody);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error formatting melody list: " + e.getMessage(), e);
            return "Error calculating melody information.";
        }
    }

//Helper method to format the MFCCs from Librosa processing in a readable way
    public String formatMFCCs(List<Float> mfccList) {
        // Validate input
        if (mfccList == null) {
            logger.warning("MFCC list is null. Returning default MFCC information.");
            return "MFCCs: [] (Total: 0)";
        }

        try {
            String firstFiveMFCCs = mfccList.subList(0, Math.min(5, mfccList.size()))
                                            .stream()
                                            .map(val -> String.format("%.2f", val))
                                            .collect(Collectors.joining(", "));
            logger.info("Formatted MFCCs: First 5 = [" + firstFiveMFCCs + "], Total = " + mfccList.size());
            return "MFCCs: [" + firstFiveMFCCs + "] (Total: " + mfccList.size() + ")";
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error formatting MFCC list: " + e.getMessage(), e);
            return "Error formatting MFCC information.";
        }
    }

//Helper method to format the Spectral Features from Librosa processing in a readable way
    public String formatSpectralFeatures(float spectralCentroid, float rms) {
        try {
            String formattedSpectralCentroid = String.format("%.2f", spectralCentroid);
            String formattedRMS = String.format("%.2f", rms);
            String result = "Spectral Features:\n- Centroid: " + formattedSpectralCentroid + "\n- RMS: " + formattedRMS;

            logger.info("Formatted spectral features. Centroid: " + formattedSpectralCentroid + ", RMS: " + formattedRMS);
            return result;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error formatting spectral features. Centroid: " + spectralCentroid + ", RMS: " + rms, e);
            return "Error formatting spectral features.";
        }
    }
 
//Method to download the user's WAV file from S3 into a temporary folder
    public String analyzeTrack(String bucketName, String keyName) throws IOException {
        // Validate inputs
        if (bucketName == null || bucketName.trim().isEmpty()) {
            throw new IllegalArgumentException("Bucket name cannot be null or empty.");
        }
        if (keyName == null || keyName.trim().isEmpty()) {
            throw new IllegalArgumentException("Key name cannot be null or empty.");
        }

        String tempDir = System.getProperty("java.io.tmpdir");
        String tempFileName = keyName.substring(keyName.lastIndexOf("/") + 1);
        String tempFilePath = tempDir + File.separator + tempFileName;

        try {
            logger.info("Downloading file from S3 for analysis: Bucket = " + bucketName + ", Key = " + keyName);
            s3FileDownloader.downloadFile(bucketName, keyName, tempFilePath);

            logger.info("File downloaded to temporary location: " + tempFilePath);
            return tempFilePath;

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error downloading file from S3: Bucket = " + bucketName + ", Key = " + keyName, e);
            throw new IOException("Failed to download file from S3 for analysis.", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error during file download: Bucket = " + bucketName + ", Key = " + keyName, e);
            throw new RuntimeException("Unexpected error during track analysis preparation.", e);
        }
    }

//Helper method to format the Beats from Librosa processing in a readable way
    public List<Integer> parseBeats(String beatsJson) throws IOException {
        return objectMapper.readValue(beatsJson, new TypeReference<List<Integer>>() {});
    }

//Helper method for the Melody from Librosa processing to be readable
    public List<Float> parseMelody(String melodyJson) throws IOException {
        return objectMapper.readValue(melodyJson, new TypeReference<List<Float>>() {});
    }

//Helper method to format the MFCCs from Librosa processing in a readable way    
    public List<Float> parseMFCCs(String mfccJson) throws IOException {
        return objectMapper.readValue(mfccJson, new TypeReference<List<Float>>() {});
    }
}
