package com.BhillionDollarApps.extrack_a_track.controllers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.BhillionDollarApps.extrack_a_track.config.S3FileDownloader;
import com.BhillionDollarApps.extrack_a_track.models.Tracks;
import com.BhillionDollarApps.extrack_a_track.repositories.TracksRepository;
import com.BhillionDollarApps.extrack_a_track.services.LibrosaService;

@Controller
@RequestMapping("/librosa")
public class LibrosaController {

    private static final Logger logger = Logger.getLogger(LibrosaController.class.getName());

    @Autowired
    private TracksRepository tracksRepository;
    @Autowired
    private LibrosaService librosaService;
    @Autowired
    private S3FileDownloader s3FileDownloader;

    //Route to send the user uploaded track to Librosa
    @PostMapping("/analyzeTrack/{trackId}")
    public String analyzeTrack(@PathVariable("trackId") Long trackId, Model model) {
        Tracks track = tracksRepository.findById(trackId)
                .orElseThrow(() -> new IllegalArgumentException("Track not found for id: " + trackId));

        if (track.getS3Key() == null || track.getS3Key().isEmpty()) {
            throw new IllegalArgumentException("No S3 key found for track with id: " + trackId);
        }

        try {
            String fileName = track.getTitle().replaceAll("\\s+", "_") + ".wav";
            Path tempFilePath = Files.createTempFile(fileName, ".wav");

            String bucketName = "extract-a-trackbucket";
            logger.info("Starting download of track for analysis with Librosa.");
            s3FileDownloader.downloadFile(bucketName, track.getS3Key(), tempFilePath.toString());

            logger.info("Track downloaded, starting Librosa analysis.");
            track = librosaService.analyzeTrackWithLibrosa(tempFilePath.toString(), track);

            logger.info("Formatting analysis results for track.");
            String formattedBeats = librosaService.formatBeats(librosaService.parseBeats(track.getBeats()));
            String formattedMelody = librosaService.formatMelody(librosaService.parseMelody(track.getMelody()));
            String formattedMFCCs = librosaService.formatMFCCs(librosaService.parseMFCCs(track.getMfcc()));

            track.setBeats(formattedBeats);
            track.setMelody(formattedMelody);
            track.setMfcc(formattedMFCCs);

            tracksRepository.save(track);

            Files.deleteIfExists(tempFilePath);
            logger.info("Temporary file deleted after analysis.");

            model.addAttribute("track", track);

        } catch (IOException e) {
            logger.severe("Error handling WAV file for analysis: " + e.getMessage());
            throw new RuntimeException("Error handling WAV file for analysis", e);
        }

        return "redirect:/tracks/" + trackId;
    }
}
