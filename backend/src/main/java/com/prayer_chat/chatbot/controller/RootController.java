package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.config.FrontendBaseUrlProvider;
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

    private final FrontendBaseUrlProvider frontendBaseUrlProvider;

    public RootController(FrontendBaseUrlProvider frontendBaseUrlProvider) {
        this.frontendBaseUrlProvider = frontendBaseUrlProvider;
    }

    /**
     * Root endpoint - returns API information
     * This prevents the OAuth2 login page from showing on the backend URL
     */
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "This is the Prayer-Chat API. Please use the frontend application.");
        response.put("frontend_url", frontendBaseUrlProvider.getBaseUrl());
        response.put("api_docs", "API documentation available at /api/docs");
        response.put("status", "active");

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
