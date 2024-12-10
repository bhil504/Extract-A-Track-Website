package com.BhillionDollarApps.extrack_a_track.controllers;

import com.BhillionDollarApps.extrack_a_track.models.LoginUser;
import com.BhillionDollarApps.extrack_a_track.models.User;
import com.BhillionDollarApps.extrack_a_track.repositories.UserRepository;
import com.BhillionDollarApps.extrack_a_track.services.SubscriptionService;
import com.BhillionDollarApps.extrack_a_track.services.UserService;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/user")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository uRepo;

    @Autowired
    private SubscriptionService subscriptionService;

    // Helper method to retrieve user ID from session
    private Long getUserIdFromSession(HttpSession session) {
        return (Long) session.getAttribute("userId");
    }

 // Register user and handle subscription
    @PostMapping("/register")
    public String registerUser(@ModelAttribute("newUser") @Valid User newUser,
                               BindingResult result,
                               Model model,
                               HttpSession session) {

        logger.info("Attempting to register user with email: {}", newUser.getEmail());

        // Step 1: Register the user
        User registeredUser = userService.register(newUser, result);
        if (result.hasErrors()) {
            logger.warn("Registration failed for email: {}. Errors: {}", newUser.getEmail(), result.getAllErrors());
            model.addAttribute("newLogin", new LoginUser());
            return "LoginAndRegister";
        }

        // Step 2: Create a Stripe customer for the user
        try {
            logger.info("Creating Stripe customer for email: {}", registeredUser.getEmail());

            Map<String, Object> params = new HashMap<>();
            params.put("email", registeredUser.getEmail());
            params.put("name", registeredUser.getUsername());

            Customer stripeCustomer = Customer.create(params);
            registeredUser.setStripeCustomerId(stripeCustomer.getId());

            // Update the user with the Stripe customer ID
            userService.updateUser(registeredUser);

            logger.info("Stripe customer created with ID: {} for user ID: {}", stripeCustomer.getId(), registeredUser.getId());
        } catch (Exception e) {
            logger.error("Failed to create Stripe customer for user ID {}: {}", registeredUser.getId(), e.getMessage());
            model.addAttribute("error", "Registration successful, but failed to set up payment. Please try again later.");
            return "LoginAndRegister";
        }

        // Step 3: Programmatically log in the user
        try {
            logger.info("Logging in user programmatically for email: {}", registeredUser.getEmail());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    registeredUser.getEmail(), null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            logger.error("Failed to authenticate user programmatically for email {}: {}", newUser.getEmail(), e.getMessage());
            model.addAttribute("newLogin", new LoginUser());
            model.addAttribute("error", "Registration successful, but login failed. Please log in manually.");
            return "LoginAndRegister";
        }

        // Step 4: Store the user ID in session and redirect to /welcome
        session.setAttribute("userId", registeredUser.getId());
        logger.info("User successfully registered and logged in with ID: {}", registeredUser.getId());
        return "redirect:/welcome";
    }

    // Login user
    @PostMapping("/login")
    public String loginUser(@ModelAttribute("newLogin") @Valid LoginUser newLogin,
                            BindingResult result,
                            Model model, HttpSession session) {

        logger.info("Attempting to log in user with email: {}", newLogin.getEmail());
        User authenticatedUser = userService.login(newLogin, result);

        if (result.hasErrors() || authenticatedUser == null) {
            logger.warn("Login failed for email: {}", newLogin.getEmail());
            model.addAttribute("newUser", new User());
            model.addAttribute("error", "Invalid email or password. Please try again.");
            return "LoginAndRegister";
        }

        // Set user ID in session
        session.setAttribute("userId", authenticatedUser.getId());
        logger.info("User successfully logged in with ID: {}", authenticatedUser.getId());
        return "redirect:/welcome";
    }

    // Show user profile page
    @GetMapping("/profile")
    public String showUserProfile(Model model, HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return "redirect:/login";
        }

        User user = userService.getUserByID(userId);
        if (user == null) {
            session.invalidate();
            return "redirect:/login";
        }

        model.addAttribute("user", user);
        return "UserProfile";
    }

    // Update username
    @PostMapping("/update-username")
    public String updateUsername(@RequestParam("newUsername") String newUsername, HttpSession session, Model model) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return "redirect:/login";
        }

        userService.updateUserUsername(userId, newUsername);
        model.addAttribute("success", "Username updated successfully.");
        return "redirect:/user/profile";
    }

    // Update email
    @PostMapping("/update-email")
    public String updateEmail(@RequestParam("newEmail") String newEmail, HttpSession session, Model model) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return "redirect:/login";
        }

        userService.updateUserEmail(userId, newEmail);
        model.addAttribute("success", "Email updated successfully.");
        return "redirect:/user/profile";
    }

    // Change password
    @PostMapping("/change-password")
    public String changePassword(@RequestParam("currentPassword") String currentPassword,
                                 @RequestParam("newPassword") String newPassword,
                                 @RequestParam("confirmNewPassword") String confirmNewPassword,
                                 HttpSession session, Model model) {

        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return "redirect:/login";
        }

        User user = userService.getUserByID(userId);
        if (user == null || !userService.validatePassword(currentPassword, user.getPassword())) {
            model.addAttribute("error", "Invalid current password.");
            return "UserProfile";
        }

        if (!newPassword.equals(confirmNewPassword)) {
            model.addAttribute("error", "New passwords do not match.");
            return "UserProfile";
        }

        userService.updateUserPassword(userId, newPassword);
        model.addAttribute("success", "Password updated successfully.");
        return "redirect:/user/profile";
    }

    // Subscribe user
    @PostMapping("/subscribe")
    public String subscribeUser(HttpSession session,
                                @RequestParam("paymentMethodId") String paymentMethodId,
                                Model model) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            logger.error("User ID not found in session. Redirecting to login.");
            return "redirect:/login";
        }

        User user = userService.getUserByID(userId);
        if (user == null) {
            logger.error("User not found for ID: " + userId + ". Invalidating session and redirecting to login.");
            session.invalidate();
            return "redirect:/login";
        }

        try {
            if (!user.isSubscribed()) {
                String customerId = user.getStripeCustomerId();
                if (customerId == null || customerId.isEmpty()) {
                    customerId = subscriptionService.createCustomer(user.getEmail(), userId);
                    user.setStripeCustomerId(customerId);
                }

                // Pass the priceId dynamically here
                String priceId = "price_1QTwJ5GxWfjXK7JXnhvh4NZk";
                Subscription subscription = subscriptionService.createSubscription(customerId, priceId, paymentMethodId);

                userService.updateUserSubscription(userId, customerId, true);
                model.addAttribute("success", "Subscription created successfully!");
            } else {
                model.addAttribute("info", "You are already subscribed.");
            }
        } catch (StripeException e) {
            logger.error("Stripe subscription creation failed: " + e.getMessage());
            model.addAttribute("error", "Subscription failed: " + e.getMessage());
        }

        return "redirect:/user/profile";
    }

    @PostMapping("/update-subscription-status")
    public ResponseEntity<String> updateSubscriptionStatus(HttpSession session,
                                                           @RequestParam("status") boolean status) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            logger.warn("User ID not found in session. Redirecting to login.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not logged in.");
        }

        try {
            userService.updateSubscriptionStatus(userId, status);
            logger.info("Subscription status updated for user ID: {} to {}", userId, status);
            return ResponseEntity.ok("Subscription status updated successfully.");
        } catch (Exception e) {
            logger.error("Failed to update subscription status for user ID: {}. Error: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Failed to update subscription status. Please try again.");
        }
    }

    @PostMapping("/unsubscribe")
    public String unsubscribeUser(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            logger.warn("User ID not found in session. Redirecting to login.");
            return "redirect:/login";
        }

        try {
            User user = userService.getUserByID(userId);
            if (user == null) {
                logger.warn("User not found for ID: {}. Redirecting to login.", userId);
                session.invalidate();
                return "redirect:/login";
            }

            // Check if the user has a Stripe subscription
            String stripeCustomerId = user.getStripeCustomerId();
            if (stripeCustomerId != null && !stripeCustomerId.isEmpty()) {
                String stripeSubscriptionId = userService.getUserStripeSubscriptionId(userId);

                if (stripeSubscriptionId != null && !stripeSubscriptionId.isEmpty()) {
                    subscriptionService.cancelSubscription(stripeSubscriptionId);
                    logger.info("Stripe subscription ID: {} canceled successfully.", stripeSubscriptionId);
                } else {
                    logger.warn("No active Stripe subscription found for user ID: {}", userId);
                }
            }

            // Update subscription status in the database
            userService.updateSubscriptionStatus(userId, false);
            logger.info("User ID: {} unsubscribed successfully.", userId);
            model.addAttribute("success", "You have successfully unsubscribed.");
        } catch (StripeException e) {
            logger.error("Stripe cancellation failed for user ID: {}. Error: {}", userId, e.getMessage());
            model.addAttribute("error", "An error occurred while processing the unsubscription. Please contact support.");
        } catch (Exception e) {
            logger.error("Failed to unsubscribe user ID: {}. Error: {}", userId, e.getMessage());
            model.addAttribute("error", "An error occurred while unsubscribing. Please try again.");
        }

        return "redirect:/user/profile";
    }



    @Transactional
    public void deleteUser(Long userId) {
        User user = uRepo.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

        try {
            // Remove from Stripe
            if (user.getStripeCustomerId() != null) {
                subscriptionService.deleteCustomer(user.getStripeCustomerId());
                logger.info("Stripe customer ID: {} deleted for user ID: {}", user.getStripeCustomerId(), userId);
            }

            // Delete user's files from S3 via UserService
            String userFolder = "user-uploads/" + user.getEmail();
            userService.deleteS3Folder(userFolder);
            logger.info("S3 folder deleted for user ID: {}", userId);

            // Remove user from the database
            uRepo.delete(user);
            logger.info("User ID: {} and associated data have been deleted.", userId);
        } catch (Exception e) {
            logger.error("Failed to delete user ID: {}. Error: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to delete user. Please try again later.");
        }
    }



    @GetMapping("/storage-usage")
    @ResponseBody
    public Map<String, Object> getUserStorageUsage(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            throw new IllegalStateException("User not logged in.");
        }

        User user = userService.getUserByID(userId);
        long usedStorage = userService.calculateUserStorage(user.getEmail());
        long maxStorage = 10L * 1024 * 1024 * 1024; // 10 GB in bytes

        Map<String, Object> response = new HashMap<>();
        response.put("usedStorage", usedStorage);
        response.put("maxStorage", maxStorage);
        response.put("percentage", (double) usedStorage / maxStorage * 100);

        return response;
    }

}
