package com.prayer_chat.chatbot.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RootController
 * 
 * Security tests:
 * - Root endpoint is publicly accessible
 * - No sensitive information is leaked
 * - Frontend URL is properly sanitized
 * - Response format is correct
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RootController Tests")
class RootControllerTest {

    @InjectMocks
    private RootController rootController;

    @BeforeEach
    void setUp() {
        // Reset controller state
    }

    @Test
    @DisplayName("Should return JSON API info for root endpoint")
    void shouldReturnApiInfo() {
        // Arrange
        ReflectionTestUtils.setField(rootController, "frontendUrl", "https://prayer-chat.com");
        ReflectionTestUtils.setField(rootController, "allowedOrigins", "http://localhost:3000,https://prayer-chat.com");

        // Act
        ResponseEntity<Map<String, Object>> response = rootController.root();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertTrue(body.containsKey("message"));
        assertTrue(body.containsKey("frontend_url"));
        assertTrue(body.containsKey("api_docs"));
        assertTrue(body.containsKey("status"));
        
        assertEquals("active", body.get("status"));
        assertTrue(body.get("message").toString().contains("Prayer-Chat API"));
    }

    @Test
    @DisplayName("Should use FRONTEND_URL environment variable when set")
    void shouldUseFrontendUrlEnvVar() {
        // Arrange
        ReflectionTestUtils.setField(rootController, "frontendUrl", "https://custom-frontend.com");
        ReflectionTestUtils.setField(rootController, "allowedOrigins", "http://localhost:3000");

        // Act
        ResponseEntity<Map<String, Object>> response = rootController.root();

        // Assert
        Map<String, Object> body = response.getBody();
        assertEquals("https://custom-frontend.com", body.get("frontend_url"));
    }

    @Test
    @DisplayName("Should extract frontend URL from CORS allowed origins")
    void shouldExtractFromCorsOrigins() {
        // Arrange
        ReflectionTestUtils.setField(rootController, "frontendUrl", "https://prayer-chat.com");
        ReflectionTestUtils.setField(rootController, "allowedOrigins", 
            "http://localhost:3000,https://prayer-chat.com,https://staging.prayer-chat.com");

        // Act
        ResponseEntity<Map<String, Object>> response = rootController.root();

        // Assert
        Map<String, Object> body = response.getBody();
        // Should prefer prayer-chat.com from CORS origins
        String frontendUrl = (String) body.get("frontend_url");
        assertTrue(frontendUrl.contains("prayer-chat.com"));
        assertFalse(frontendUrl.contains("localhost"));
    }

    @Test
    @DisplayName("Should skip localhost URLs when extracting from CORS")
    void shouldSkipLocalhostUrls() {
        // Arrange
        ReflectionTestUtils.setField(rootController, "frontendUrl", "https://prayer-chat.com");
        ReflectionTestUtils.setField(rootController, "allowedOrigins", 
            "http://localhost:3000,http://127.0.0.1:3000,https://production.com");

        // Act
        ResponseEntity<Map<String, Object>> response = rootController.root();

        // Assert
        Map<String, Object> body = response.getBody();
        String frontendUrl = (String) body.get("frontend_url");
        assertFalse(frontendUrl.contains("localhost"));
        assertFalse(frontendUrl.contains("127.0.0.1"));
        assertTrue(frontendUrl.contains("production.com") || frontendUrl.contains("prayer-chat.com"));
    }

    @Test
    @DisplayName("Should use default frontend URL when no configuration available")
    void shouldUseDefaultFrontendUrl() {
        // Arrange
        ReflectionTestUtils.setField(rootController, "frontendUrl", "https://prayer-chat.com");
        ReflectionTestUtils.setField(rootController, "allowedOrigins", "");

        // Act
        ResponseEntity<Map<String, Object>> response = rootController.root();

        // Assert
        Map<String, Object> body = response.getBody();
        assertEquals("https://prayer-chat.com", body.get("frontend_url"));
    }

    @Test
    @DisplayName("Should not leak sensitive information in response")
    void shouldNotLeakSensitiveInfo() {
        // Arrange
        ReflectionTestUtils.setField(rootController, "frontendUrl", "https://prayer-chat.com");
        ReflectionTestUtils.setField(rootController, "allowedOrigins", "http://localhost:3000");

        // Act
        ResponseEntity<Map<String, Object>> response = rootController.root();

        // Assert
        Map<String, Object> body = response.getBody();
        String bodyString = body.toString().toLowerCase();
        
        // Should not contain sensitive information
        assertFalse(bodyString.contains("password"));
        assertFalse(bodyString.contains("secret"));
        assertFalse(bodyString.contains("key"));
        assertFalse(bodyString.contains("token"));
        assertFalse(bodyString.contains("api_key"));
        assertFalse(bodyString.contains("database"));
    }

    @Test
    @DisplayName("Should handle null frontend URL gracefully")
    void shouldHandleNullFrontendUrl() {
        // Arrange
        ReflectionTestUtils.setField(rootController, "frontendUrl", null);
        ReflectionTestUtils.setField(rootController, "allowedOrigins", "https://prayer-chat.com");

        // Act
        ResponseEntity<Map<String, Object>> response = rootController.root();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        // Should fallback to CORS or default
        assertNotNull(response.getBody().get("frontend_url"));
    }

    @Test
    @DisplayName("Should handle empty CORS origins gracefully")
    void shouldHandleEmptyCorsOrigins() {
        // Arrange
        ReflectionTestUtils.setField(rootController, "frontendUrl", "https://prayer-chat.com");
        ReflectionTestUtils.setField(rootController, "allowedOrigins", null);

        // Act
        ResponseEntity<Map<String, Object>> response = rootController.root();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertEquals("https://prayer-chat.com", body.get("frontend_url"));
    }
}

