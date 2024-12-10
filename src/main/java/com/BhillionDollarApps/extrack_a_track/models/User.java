package com.BhillionDollarApps.extrack_a_track.models;

import java.util.Date;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Unique identifier for each user

    @NotEmpty(message = "Email is required!")
    @Email(message = "Please enter a valid email!")
    private String email;
    
    @NotEmpty(message = "Username is required!")
    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    private String username;

    @NotEmpty(message = "Password is required!")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String password;
    
    @Transient
    @NotEmpty(message = "Confirm Password is required!")
    @Size(min = 8, max = 128, message = "Confirm Password must match Password!")
    private String confirm;
    
    private boolean subscribed = false; // Database field to indicate subscription status
    
    private String stripeCustomerId;

    @Transient
    private boolean subscribe; // Transient field to capture checkbox value from form

    @Column(updatable = false)
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date createdAt;
    
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date updatedAt;
    
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Tracks> tracks;

    public User() {}

    public User(String email, String username, String password, String confirm, Date createdAt, Date updatedAt,
                List<Tracks> tracks) {
        this.email = email;
        this.username = username;
        this.password = password;
        this.confirm = confirm;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.tracks = tracks;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = new Date();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = new Date();
    }
    
    // Getters and Setters
    
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public void setStripeCustomerId(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirm() {
        return confirm;
    }

    public void setConfirm(String confirm) {
        this.confirm = confirm;
    }

    public boolean isSubscribed() {
        return subscribed;
    }

    public void setSubscribed(boolean subscribed) {
        this.subscribed = subscribed;
    }

    public boolean isSubscribe() {
        return subscribe;
    }

    public void setSubscribe(boolean subscribe) {
        this.subscribe = subscribe;
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

    public List<Tracks> getTracks() {
        return tracks;
    }

    public void setTracks(List<Tracks> tracks) {
        this.tracks = tracks;
    }
}
