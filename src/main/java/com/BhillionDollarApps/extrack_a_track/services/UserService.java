package com.BhillionDollarApps.extrack_a_track.services;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import com.BhillionDollarApps.extrack_a_track.models.LoginUser;
import com.BhillionDollarApps.extrack_a_track.models.User;
import com.BhillionDollarApps.extrack_a_track.repositories.UserRepository;
import jakarta.transaction.Transactional;

@Service
public class UserService {

   
	 private static final Logger logger = Logger.getLogger(UserService.class.getName());

    @Autowired
    private UserRepository uRepo;
    @Autowired
    private SubscriptionService subscriptionService;
    @Autowired
    private S3Client s3Client;
 


// Register a new user
    public User register(User newUser, BindingResult result) {
        if (uRepo.findByEmail(newUser.getEmail()).isPresent()) {
            result.rejectValue("email", "Matches", "An account with this email already exists.");
        }

        if (!newUser.getPassword().equals(newUser.getConfirm())) {
            result.rejectValue("confirm", "Matches", "The Confirm Password must match Password!");
        }

        if (result.hasErrors()) {
            return null;
        }

        newUser.setPassword(BCrypt.hashpw(newUser.getPassword(), BCrypt.gensalt(10)));
        logger.log(Level.INFO, "New user registered successfully with email: {0}", newUser.getEmail());
        return uRepo.save(newUser);
    }

// Retrieve a user by ID
    public User getUserByID(Long id) {
        return uRepo.findById(id).orElse(null);
    }

// Validate a password against a hashed password
    public boolean validatePassword(String rawPassword, String hashedPassword) {
        boolean matches = BCrypt.checkpw(rawPassword, hashedPassword);
        if (matches) {
            logger.log(Level.INFO, "Password validation successful.");
        } else {
            logger.log(Level.WARNING, "Password validation failed.");
        }
        return matches;
    }

// Update a user's password
    public void updateUserPassword(Long userId, String rawPassword) {
        User user = uRepo.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Hash the new raw password with bcrypt
        String hashedPassword = BCrypt.hashpw(rawPassword, BCrypt.gensalt(10));

        user.setPassword(hashedPassword);
        uRepo.save(user);

        logger.log(Level.INFO, "Password updated successfully for user ID: {0}", userId);
    }


// General user update
    @Transactional
    public User updateUser(User u) {
        return uRepo.findById(u.getId()).map(existingUser -> {
            // Update necessary fields only if they are not null or empty
            if (u.getPassword() != null && !u.getPassword().isEmpty()) {
                existingUser.setPassword(u.getPassword());
            }
            if (u.getEmail() != null && !u.getEmail().isEmpty()) {
                existingUser.setEmail(u.getEmail());
            }
            if (u.getUsername() != null && !u.getUsername().isEmpty()) {
                existingUser.setUsername(u.getUsername());
            }

            try {
                // Save the updated user
                User updatedUser = uRepo.save(existingUser);
                logger.log(Level.INFO, "User updated successfully with ID: {0}", updatedUser.getId());
                return updatedUser;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to update user with ID: {0}. Error: {1}", new Object[]{u.getId(), e.getMessage()});
                throw new RuntimeException("Failed to update user with ID: " + u.getId(), e);
            }
        }).orElseThrow(() -> {
            logger.log(Level.WARNING, "User not found with ID: {0}", u.getId());
            return new IllegalArgumentException("User with ID " + u.getId() + " not found.");
        });
    }

//Update Subscription Stats
    @Transactional
    public void updateSubscriptionStatus(Long userId, boolean status) {
        uRepo.findById(userId).ifPresent(user -> {
            user.setSubscribed(status);
            uRepo.save(user);
            logger.log(Level.INFO, "Updated subscription status for user ID: {0} to {1}", new Object[]{userId, status});
        });
    }

// Update the username of a user
    public void updateUserUsername(Long userId, String newUsername) {
        Optional<User> optionalUser = uRepo.findById(userId);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setUsername(newUsername);
            uRepo.save(user);
            logger.log(Level.INFO, "Username updated successfully for user ID: {0}", userId);
        } else {
            logger.log(Level.WARNING, "Failed to update username: User with ID {0} not found.", userId);
        }
    }

// Update the email of a user
    public void updateUserEmail(Long userId, String newEmail) {
        Optional<User> optionalUser = uRepo.findById(userId);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setEmail(newEmail);
            uRepo.save(user);
            logger.log(Level.INFO, "Email updated successfully for user ID: {0}", userId);
        } else {
            logger.log(Level.WARNING, "Failed to update email: User with ID {0} not found.", userId);
        }
    }

// Log in a user by checking their email and password
    public User login(LoginUser loginUser, BindingResult result) {
        logger.log(Level.INFO, "Attempting to log in user with email: {0}", loginUser.getEmail());

        Optional<User> potentialUser = uRepo.findByEmail(loginUser.getEmail());

        if (!potentialUser.isPresent()) {
            result.rejectValue("email", "NotFound", "Email not found.");
            logger.log(Level.WARNING, "Login failed: email not found - {0}", loginUser.getEmail());
            return null;
        }

        User user = potentialUser.get();

        // Check if the password matches the hashed password
        if (!BCrypt.checkpw(loginUser.getPassword(), user.getPassword())) {
            result.rejectValue("password", "Invalid", "Invalid password.");
            logger.log(Level.WARNING, "Login failed: invalid password for email - {0}", loginUser.getEmail());
            return null;
        }

        logger.log(Level.INFO, "User logged in successfully with email: {0}", loginUser.getEmail());
        return user;
    }
    
// Method to update the user's subscription in the RDS
    public void updateUserSubscription(Long userId, String stripeCustomerId, boolean subscribed) {
        Optional<User> optionalUser = uRepo.findById(userId);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setStripeCustomerId(stripeCustomerId);
            user.setSubscribed(subscribed);
            uRepo.save(user);
            logger.log(Level.INFO, "User subscription status updated successfully for user ID: {0}", userId);
        } else {
            logger.log(Level.WARNING, "Failed to update subscription: User with ID {0} not found.", userId);
        }
    }

//Method to unsubscribe the user from Stripe
    public void unsubscribe(Long userId) {
        User user = uRepo.findById(userId).orElse(null);
        if (user != null) {
            user.setSubscribed(false); // Assuming you have a 'subscribed' field
            uRepo.save(user);
        }
    }

//Method to delete the user from Stripe, Delete the user's files, and remove the user from the database
    @Transactional
    public void deleteUser(Long userId) {
        User user = uRepo.findById(userId).orElseThrow(() -> 
            new IllegalArgumentException("User not found")
        );

        // Step 1: Remove from Stripe
        if (user.getStripeCustomerId() != null) {
            subscriptionService.deleteCustomer(user.getStripeCustomerId());
        }

        // Step 2: Delete user's files from S3
        String userFolder = "user-uploads/" + user.getEmail();
        deleteS3Folder(userFolder);

        // Step 3: Remove user from the database
        uRepo.delete(user);

        logger.info(String.format("User ID: %d and all associated data have been deleted.", userId));
    }
 
//Method to delete a folder from S3
    public void deleteS3Folder(String folderPath) {
        String bucketName = "extract-a-trackbucket"; // Replace with your actual bucket name

        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
            .bucket(bucketName)
            .prefix(folderPath + "/")
            .build();

        ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

        listResponse.contents().forEach(object -> {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(object.key())
                .build();
            s3Client.deleteObject(deleteRequest);
            logger.info(String.format("Deleted object: %s", object.key()));
        });

        logger.info(String.format("Deleted folder: %s", folderPath));
    }

//Method to calculate how much data the user is using in S3
    public long calculateUserStorage(String userEmail) {
        String userFolder = "user-uploads/" + userEmail;
        String bucketName = "extract-a-trackbucket"; // Replace with your bucket name

        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
            .bucket(bucketName)
            .prefix(userFolder + "/")
            .build();

        ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

        return listResponse.contents().stream()
            .mapToLong(object -> object.size()) // Sum up the size of all objects
            .sum();
    }

    public String getUserStripeSubscriptionId(Long userId) {
        Optional<User> optionalUser = uRepo.findById(userId);
        if (optionalUser.isPresent()) {
            String subscriptionId = optionalUser.get().getStripeCustomerId();
            if (subscriptionId != null && !subscriptionId.isEmpty()) {
                logger.log(Level.INFO, "Stripe subscription ID retrieved for user ID: {0}", userId);
                return subscriptionId;
            } else {
                logger.log(Level.WARNING, "No subscription ID found for user ID: {0}", userId);
            }
        } else {
            logger.log(Level.WARNING, "User not found for ID: {0}", userId);
        }
        return null; // No subscription ID found
    }

}




