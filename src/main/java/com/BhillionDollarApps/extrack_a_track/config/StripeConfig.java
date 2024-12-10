package com.BhillionDollarApps.extrack_a_track.config;

import com.stripe.Stripe;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import javax.annotation.PostConstruct;

@Configuration
public class StripeConfig {

    @Value("${stripe.api.secret.key}")
    private String stripeApiKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

}
