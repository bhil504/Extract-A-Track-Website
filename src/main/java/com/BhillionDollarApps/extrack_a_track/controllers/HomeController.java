package com.BhillionDollarApps.extrack_a_track.controllers;

import com.BhillionDollarApps.extrack_a_track.models.LoginUser;
import com.BhillionDollarApps.extrack_a_track.models.User;
import com.BhillionDollarApps.extrack_a_track.models.Tracks;
import com.BhillionDollarApps.extrack_a_track.services.UserService;
import com.BhillionDollarApps.extrack_a_track.services.TracksService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class HomeController {

    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    @Autowired
    private UserService userService;
    @Autowired
    private TracksService tracksService;
    

//Route for the Login and Registration form
    @GetMapping("/")
    public String index(Model model, HttpSession session) {
        if (session.getAttribute("userId") != null) {
            logger.info("User already logged in. Redirecting to /welcome.");
            return "redirect:/welcome";
        }
        model.addAttribute("newUser", new User());
        model.addAttribute("newLogin", new LoginUser());
        return "LoginAndRegister";
    }

//Route to display the user's dashboard
    @GetMapping("/welcome")
    public String dashboard(Model model, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            logger.warn("No user logged in. Redirecting to /.");
            return "redirect:/";
        }

        User user = userService.getUserByID(userId);
        if (user == null) {
            logger.warn("User with ID {} not found. Invalidating session and redirecting to /.", userId);
            session.invalidate();
            return "redirect:/";
        }

        // Populate model with user details and tracks
        model.addAttribute("user", user);
        List<Tracks> userTracks = tracksService.findTracksByUserId(userId);
        model.addAttribute("userTracks", userTracks);

        logger.info("Displaying dashboard for user ID: {}", userId);
        return "Dashboard";
    }
}
