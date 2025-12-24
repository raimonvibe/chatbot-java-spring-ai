package com.prayer_chat.chatbot.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Root controller for API endpoint
 * Returns JSON response instead of redirecting to OAuth2 login
 */
@RestController
public class RootController {

    @Value("${FRONTEND_URL:https://prayer-chat.com}")
    private String frontendUrl;

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    /**
     * Root endpoint - returns API information
     * This prevents the OAuth2 login page from showing on the backend URL
     */
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "This is the Prayer-Chat API. Please use the frontend application.");
        response.put("frontend_url", getFrontendUrl());
        response.put("api_docs", "API documentation available at /api/docs");
        response.put("status", "active");
        
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Get the frontend URL from configuration
     * Priority: 1. FRONTEND_URL env var, 2. First non-localhost from CORS, 3. Default
     */
    private String getFrontendUrl() {
        // Use explicit FRONTEND_URL if set
        if (frontendUrl != null && !frontendUrl.trim().isEmpty() && !frontendUrl.equals("https://prayer-chat.com")) {
            return frontendUrl.trim();
        }

        // Try to extract production URL from CORS allowed origins
        if (allowedOrigins != null && !allowedOrigins.trim().isEmpty()) {
            String[] origins = allowedOrigins.split(",");
            for (String origin : origins) {
                String trimmed = origin.trim();
                // Skip localhost
                if (!trimmed.startsWith("http://localhost") && !trimmed.startsWith("http://127.0.0.1")) {
                    // Prefer prayer-chat.com if available
                    if (trimmed.contains("prayer-chat.com")) {
                        return trimmed;
                    }
                }
            }
            // Return first non-localhost if prayer-chat.com not found
            for (String origin : origins) {
                String trimmed = origin.trim();
                if (!trimmed.startsWith("http://localhost") && !trimmed.startsWith("http://127.0.0.1")) {
                    return trimmed;
                }
            }
        }

        // Default fallback
        return "https://prayer-chat.com";
    }
}

