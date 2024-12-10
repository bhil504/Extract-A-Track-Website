package com.BhillionDollarApps.extrack_a_track.models;


import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "tracks")
public class Tracks {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Primary key for the Tracks entity

    @Transient // This field won't be persisted
    private MultipartFile file;

    @NotEmpty(message = "Title is required!")
    @Size(min = 3, max = 30, message = "Title must be between 3 and 30 characters")
    private String title;

    @NotEmpty(message = "Artist is required!")
    @Size(min = 1, max = 30, message = "Artist must be between 1 and 30 characters!")
    private String artist;

    @NotEmpty(message = "Genre is required!")
    @Size(min = 1, max = 30, message = "Genre must be between 1 and 30 characters!")
    private String genre;

    @NotBlank(message = "Lyrics are required!")
    @Lob
    private String lyrics;
    
    @Lob
    @Column(name = "original_wav")
    private byte[] originalWav;
    
    // Store the converted MP3 file as binary data
    @Lob
    @Column(name = "converted_mp3")
    private byte[] convertedMp3;

    private Float tempo;
    private Float spectralCentroid;
    private Float rms;
    private String s3Key;
    
    @Column(name = "status")
    private String status;
    
 // Fields for analysis and metadata
    private String songKey;
    
    @Lob
    @Column(name = "beats", columnDefinition = "TEXT")
    private String beats;

    @Lob
    @Column(name = "melody", columnDefinition = "TEXT")
    private String melody;

    @Lob
    @Column(name = "mfcc", columnDefinition = "TEXT")
    private String mfcc;

    @Lob
    @Column(name = "spectral_features", columnDefinition = "TEXT")
    private String spectralFeatures;

    // Fields for file metadata
    @Column(name = "file_name")
    private String fileName;
    

    @Column(name = "Mp3S3Key")
    private String Mp3S3Key;

    @Column(name = "vocals")
    private String vocals;

    @Column(name = "bass")
    private String bass;

    @Column(name = "drums")
    private String drums;

    @Column(name = "other")
    private String other;
    
    @Column(name = "accompaniment")
    private String accompaniment;

    @Column(name = "piano")
    private String piano;

	@Column(updatable = false)
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date createdAt;
	
	@DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date updatedAt;

    // Constructors
    public Tracks() {
        this.status = "PENDING"; // Default status for new tracks  
    }

	public Tracks(Long id, MultipartFile file,
			@NotEmpty(message = "Title is required!") @Size(min = 3, max = 30, message = "Title must be between 3 and 30 characters") String title,
			@NotEmpty(message = "Artist is required!") @Size(min = 1, max = 30, message = "Artist must be between 1 and 30 characters!") String artist,
			@NotEmpty(message = "Genre is required!") @Size(min = 1, max = 30, message = "Genre must be between 1 and 30 characters!") String genre,
			@NotBlank(message = "Lyrics are required!") String lyrics, byte[] originalWav, byte[] convertedMp3,
			Float tempo, Float spectralCentroid, Float rms, String s3Key, String status, String songKey, String beats,
			String melody, String mfcc, String spectralFeatures, String fileName, String mp3s3Key, String vocals,
			String bass, String drums, String other, String accompaniment, String piano, Date createdAt, Date updatedAt,
			User user) {
		super();
		this.id = id;
		this.file = file;
		this.title = title;
		this.artist = artist;
		this.genre = genre;
		this.lyrics = lyrics;
		this.originalWav = originalWav;
		this.convertedMp3 = convertedMp3;
		this.tempo = tempo;
		this.spectralCentroid = spectralCentroid;
		this.rms = rms;
		this.s3Key = s3Key;
		this.status = status;
		this.songKey = songKey;
		this.beats = beats;
		this.melody = melody;
		this.mfcc = mfcc;
		this.spectralFeatures = spectralFeatures;
		this.fileName = fileName;
		Mp3S3Key = mp3s3Key;
		this.vocals = vocals;
		this.bass = bass;
		this.drums = drums;
		this.other = other;
		this.accompaniment = accompaniment;
		this.piano = piano;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.user = user;
	}

	@PrePersist
    protected void onCreate() {
        this.createdAt = new Date();
        this.status = "PENDING"; // Set status to PENDING when track is created
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = new Date();
       }
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
	// Validating file type and size
    @AssertTrue(message = "Only audio files (mp3, wav) are allowed, and must be less than 50MB.")
    public boolean isValidFile() {
        if (file == null || file.isEmpty()) return true; // Allow no file to be uploaded
        return file.getSize() <= 75 * 1024 * 1024 && (file.getContentType().equals("audio/mpeg") || file.getContentType().equals("audio/wav"));
    }
    
  //Getters And Setters

	public Long getId() {
		return id;
	}

	public byte[] getOriginalWav() {
		return originalWav;
	}

	public void setOriginalWav(byte[] originalWav) {
		this.originalWav = originalWav;
	}

	public byte[] getConvertedMp3() {
		return convertedMp3;
	}

	public void setConvertedMp3(byte[] convertedMp3) {
		this.convertedMp3 = convertedMp3;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public MultipartFile getFile() {
		return file;
	}

	public void setFile(MultipartFile file) {
		this.file = file;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getArtist() {
		return artist;
	}

	public void setArtist(String artist) {
		this.artist = artist;
	}

	public String getGenre() {
		return genre;
	}

	public void setGenre(String genre) {
		this.genre = genre;
	}

	public String getLyrics() {
		return lyrics;
	}

	public void setLyrics(String lyrics) {
		this.lyrics = lyrics;
	}

	public Float getTempo() {
		return tempo;
	}

	public void setTempo(Float tempo) {
		this.tempo = tempo;
	}

	public Float getSpectralCentroid() {
		return spectralCentroid;
	}

	public void setSpectralCentroid(Float spectralCentroid) {
		this.spectralCentroid = spectralCentroid;
	}

	public Float getRms() {
		return rms;
	}

	public void setRms(Float rms) {
		this.rms = rms;
	}

	public String getS3Key() {
		return s3Key;
	}

	public void setS3Key(String s3Key) {
		this.s3Key = s3Key;
	}

	public Date getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
	}

	public Date getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Date updatedAt) {
		this.updatedAt = updatedAt;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getSongKey() {
		return songKey;
	}

	public void setSongKey(String songKey) {
		this.songKey = songKey;
	}

	public String getBeats() {
		return beats;
	}

	public void setBeats(String beats) {
		this.beats = beats;
	}

	public String getMelody() {
		return melody;
	}

	public void setMelody(String melody) {
		this.melody = melody;
	}

	public String getMfcc() {
		return mfcc;
	}

	public void setMfcc(String mfcc) {
		this.mfcc = mfcc;
	}

	public String getSpectralFeatures() {
		return spectralFeatures;
	}

	public void setSpectralFeatures(String spectralFeatures) {
		this.spectralFeatures = spectralFeatures;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getMp3S3Key() {
		return Mp3S3Key;
	}

	public void setMp3S3Key(String mp3s3Key) {
		Mp3S3Key = mp3s3Key;
	}

	public String getVocals() {
		return vocals;
	}

	public void setVocals(String vocals) {
		this.vocals = vocals;
	}

	public String getBass() {
		return bass;
	}

	public void setBass(String bass) {
		this.bass = bass;
	}

	public String getDrums() {
		return drums;
	}

	public void setDrums(String drums) {
		this.drums = drums;
	}

	public String getOther() {
		return other;
	}

	public void setOther(String other) {
		this.other = other;
	}

	public String getAccompaniment() {
		return accompaniment;
	}

	public void setAccompaniment(String accompaniment) {
		this.accompaniment = accompaniment;
	}

	public String getPiano() {
		return piano;
	}

	public void setPiano(String piano) {
		this.piano = piano;
	}
    
    
	public String getFieldValue(String fieldName) {
	    switch (fieldName.toLowerCase()) {
	        case "vocals":
	            return getVocals();
	        case "accompaniment":
	            return getAccompaniment();
	        case "piano":
	            return getPiano();
	        case "bass":
	            return getBass();
	        case "drums":
	            return getDrums();
	        case "other":
	            return getOther();
	        case "mp3s3key":
	            return getMp3S3Key();
	        default:
	            throw new IllegalArgumentException("Field name not recognized: " + fieldName);
	    }
	}

	
	
	

}