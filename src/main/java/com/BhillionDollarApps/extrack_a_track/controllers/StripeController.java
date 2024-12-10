package com.BhillionDollarApps.extrack_a_track.controllers;

import com.BhillionDollarApps.extrack_a_track.models.User;
import com.BhillionDollarApps.extrack_a_track.services.SubscriptionService;
import com.BhillionDollarApps.extrack_a_track.services.UserService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.SetupIntent;
import com.stripe.param.SetupIntentCreateParams;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StripeController {

    @Value("${stripe.api.secret.key}")
    private String stripeApiKey;

    @Autowired
    private UserService userService;
    @Autowired
    private SubscriptionService subscriptionService;

    @GetMapping("/create-setup-intent")
    public ResponseEntity<?> createSetupIntent(@RequestParam(required = false) String customerId, HttpSession session) {
        try {
            // Set the Stripe API Key
            Stripe.apiKey = stripeApiKey;

            // Retrieve the userId from session
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                return ResponseEntity.badRequest().body("User session is missing. Please log in again.");
            }

            // Retrieve the user from the database
            User user = userService.getUserByID(userId);
            if (user == null) {
                return ResponseEntity.badRequest().body("User not found. Please log in again.");
            }

            // Validate or assign the customerId
            if (customerId == null || customerId.trim().isEmpty()) {
                customerId = user.getStripeCustomerId();
                if (customerId == null || customerId.trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("Stripe Customer ID is missing. Please subscribe first.");
                }
            }

            // Create SetupIntentCreateParams
            SetupIntentCreateParams params = SetupIntentCreateParams.builder()
            	    .setCustomer(customerId)
            	    .addPaymentMethodType("card")
            	    .build();


            // Create the SetupIntent via Stripe API
            SetupIntent intent = SetupIntent.create(params);

            // Return the client secret for the SetupIntent
            return ResponseEntity.ok(intent.getClientSecret());
        } catch (StripeException e) {
            // Log the exception and return error details
            e.printStackTrace();
            return ResponseEntity.status(500).body("Stripe API error: " + e.getMessage());
        } catch (Exception e) {
            // Handle general exceptions
            e.printStackTrace();
            return ResponseEntity.status(500).body("An error occurred: " + e.getMessage());
        }
    }
    
    @PostMapping("/subscribe")
    public String subscribe(@RequestParam String customerId, 
                            @RequestParam String paymentMethodId) {
        try {
            // Retrieve priceId from application.properties
            String priceId = "price_1QTwJ5GxWfjXK7JXnhvh4NZk"; // Replace with your actual price ID if not using @Value

            // Call the createSubscription method with all three parameters
            subscriptionService.createSubscription(customerId, priceId, paymentMethodId);
            return "Subscription created successfully.";
        } catch (StripeException e) {
            return "Error creating subscription: " + e.getMessage();
        }
    }

}
