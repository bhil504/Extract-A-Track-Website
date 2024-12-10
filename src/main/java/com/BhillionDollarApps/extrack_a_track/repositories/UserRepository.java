package com.BhillionDollarApps.extrack_a_track.repositories;


import org.springframework.data.jpa.repository.JpaRepository;

import com.BhillionDollarApps.extrack_a_track.models.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    User findByStripeCustomerId(String stripeCustomerId);
}