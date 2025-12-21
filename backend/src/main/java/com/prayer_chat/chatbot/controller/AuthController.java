package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication Controller
 * Handles OAuth2 authentication (Google login only)
 * 
 * Note: Email/password login has been removed. Users must authenticate via Google OAuth2.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Get current user info
     * Returns information about the currently authenticated user (via OAuth2)
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal CustomOAuth2User oAuth2User) {
        if (oAuth2User == null) {
            // Fallback: try to get from SecurityContext (for backward compatibility)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            Object principal = authentication.getPrincipal();
            User user;
            
            if (principal instanceof CustomOAuth2User) {
                user = ((CustomOAuth2User) principal).getUser();
            } else if (principal instanceof User) {
                user = (User) principal;
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("id", user.getId());
            response.put("username", user.getUsername());
            response.put("email", user.getEmail());
            response.put("roles", user.getRoles());
            response.put("authProvider", user.getAuthProvider());

            return ResponseEntity.ok(response);
        }

        User user = oAuth2User.getUser();

        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        response.put("roles", user.getRoles());
        response.put("authProvider", user.getAuthProvider());

        return ResponseEntity.ok(response);
    }
}
