package com.BhillionDollarApps.extrack_a_track.services;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.stripe.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Service
public class SubscriptionService {
	
	  private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

	@Value("${stripe.api.secret.key}")
    private String stripeApiKey;

    @Value("${stripe.api.price.id}")
    private String priceId;

 // Create a Stripe customer with email and optional user metadata
    public String createCustomer(String email, Long userId) throws StripeException {
        Stripe.apiKey = stripeApiKey;

        Map<String, Object> params = new HashMap<>();
        params.put("email", email);
        params.put("metadata", Map.of("userId", userId.toString()));

        Customer customer = Customer.create(params);
        return customer.getId();
    }

 // Attach a payment method to a customer
    public PaymentMethod attachPaymentMethod(String paymentMethodId, String customerId) throws StripeException {
        PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
        paymentMethod.attach(Map.of("customer", customerId));
        return paymentMethod;
    }

 // Remove the hardcoded priceId and allow it to be passed dynamically
    public Subscription createSubscription(String customerId, String priceId, String paymentMethodId) throws StripeException {
        Stripe.apiKey = stripeApiKey;

        // Attach the payment method to the customer
        attachPaymentMethod(paymentMethodId, customerId);

        // Set the default payment method for the customer
        Customer customer = Customer.retrieve(customerId);
        customer.update(Map.of(
            "invoice_settings", Map.of("default_payment_method", paymentMethodId)
        ));

        // Create the subscription with the dynamic priceId
        Map<String, Object> params = Map.of(
            "customer", customerId,
            "items", List.of(Map.of("price", priceId)),
            "expand", List.of("latest_invoice.payment_intent")
        );

        return Subscription.create(params);
    }
    
    public void cancelSubscription(String subscriptionId) throws StripeException {
        Stripe.apiKey = stripeApiKey;

        Subscription subscription = Subscription.retrieve(subscriptionId);
        subscription.cancel(); // Cancels the subscription immediately
        logger.info("Stripe subscription with ID: {} has been canceled.", subscriptionId);
    }



// Create a Stripe customer with email and optional user metadata    
    public String createStripeCustomer(String email, String userId) throws StripeException {
        Map<String, Object> params = Map.of(
            "email", email,
            "metadata", Map.of("userId", userId)
        );
        Customer customer = Customer.create(params);
        return customer.getId();
    }
    
 // Method to delete a customer from Stripe
    public void deleteCustomer(String customerId) {
        try {
            Customer customer = Customer.retrieve(customerId);
            customer.delete();
            System.out.println("Stripe customer with ID: " + customerId + " deleted successfully.");
        } catch (StripeException e) {
            System.err.println("Error deleting Stripe customer ID: " + customerId + ". Message: " + e.getMessage());
            throw new RuntimeException("Failed to delete Stripe customer.");
        }
    }


}
