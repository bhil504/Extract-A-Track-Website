package com.BhillionDollarApps.extrack_a_track.utils;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class SpleeterUtility {

    private static final Logger logger = Logger.getLogger(SpleeterUtility.class.getName());
    private static final String SPLEETER_COMMAND = "spleeter separate -p spleeter:4stems -o";

    /**
     * Extracts stems from a given audio file using Spleeter.
     *
     * @param inputFilePath    The path to the original audio file.
     * @param outputFolderPath The folder path where the extracted stems should be stored.
     * @return A map containing the paths to the extracted stem files.
     * @throws IOException          if an error occurs during processing.
     * @throws InterruptedException if the Spleeter command is interrupted.
     */
    public Map<String, String> extractStems(String inputFilePath, String outputFolderPath) throws IOException, InterruptedException {
        if (inputFilePath == null || outputFolderPath == null) {
            throw new IllegalArgumentException("Input and output paths cannot be null.");
        }

        // Ensure the output folder exists
        File outputFolder = new File(outputFolderPath);
        if (!outputFolder.exists() && !outputFolder.mkdirs()) {
            throw new IOException("Failed to create output folder: " + outputFolderPath);
        }

        // Construct the command to run Spleeter
        String command = String.format("%s \"%s\" \"%s\"", SPLEETER_COMMAND, outputFolderPath, inputFilePath);
        logger.info("Executing Spleeter command: " + command);

        // Execute the Spleeter command
        Process process = Runtime.getRuntime().exec(command);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("Spleeter processing failed with exit code " + exitCode);
        }

        // Prepare the map of output stem paths
        Map<String, String> stemPaths = new HashMap<>();
        stemPaths.put("bass", Paths.get(outputFolderPath, "bass.wav").toString());
        stemPaths.put("piano", Paths.get(outputFolderPath, "piano.wav").toString());
        stemPaths.put("vocals", Paths.get(outputFolderPath, "vocals.wav").toString());
        stemPaths.put("drums", Paths.get(outputFolderPath, "drums.wav").toString());

        logger.info("Spleeter processing completed. Generated stems: " + stemPaths);
        return stemPaths;
    }

    /**
     * Converts extracted stems to other formats if needed.
     * Placeholder for future implementation.
     *
     * @param inputFilePath    The path to the original audio file.
     * @param outputFolderPath The folder path where the converted files should be stored.
     * @return A map containing the paths to the converted files.
     */
    public Map<String, String> convertToOtherFormats(String inputFilePath, String outputFolderPath) {
        logger.warning("Conversion logic not implemented. Returning empty map.");
        return new HashMap<>();
    }

    /**
     * Prepares a file for an HTTP POST request as a multi-part form-data body.
     *
     * @param filePath The path to the file to be sent.
     * @return A `BodyPublisher` for the HTTP request.
     * @throws IOException If the file cannot be read.
     */
    public static HttpRequest.BodyPublisher ofFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + filePath);
        }

        String boundary = "JavaBoundary";
        List<byte[]> byteArrays = new ArrayList<>();

        // Add prefix for the file
        byteArrays.add(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        byteArrays.add(("Content-Disposition: form-data; name=\"file\"; filename=\"" + path.getFileName() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        byteArrays.add(("Content-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        byteArrays.add(Files.readAllBytes(path));
        byteArrays.add(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        logger.info("Prepared file for HTTP POST: " + filePath);
        return HttpRequest.BodyPublishers.ofByteArrays(byteArrays);
    }
}
