package com.BhillionDollarApps.extrack_a_track.utils;



import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordUtils {

    private static final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();

    // Method to hash the password
    public static String hashPassword(String plainPassword) {
        return bCryptPasswordEncoder.encode(plainPassword);
    }

}
