package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.config.FrontendBaseUrlProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests for RootController
 *
 * Security tests:
 * - Root endpoint is publicly accessible
 * - No sensitive information is leaked
 * - Frontend URL comes from {@link FrontendBaseUrlProvider} (configuration only)
 * - Response format is correct
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RootController Tests")
class RootControllerTest {

    @Mock
    private FrontendBaseUrlProvider frontendBaseUrlProvider;

    @InjectMocks
    private RootController rootController;

    @BeforeEach
    void setUp() {
        lenient().when(frontendBaseUrlProvider.getBaseUrl()).thenReturn("https://prayer-chat.com");
    }

    @Test
    @DisplayName("Should return JSON API info for root endpoint")
    void shouldReturnApiInfo() {
        ResponseEntity<Map<String, Object>> response = rootController.root();

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
    @DisplayName("Should expose configured frontend base URL from provider")
    void shouldExposeConfiguredFrontendBaseUrl() {
        when(frontendBaseUrlProvider.getBaseUrl()).thenReturn("https://custom-frontend.com");

        ResponseEntity<Map<String, Object>> response = rootController.root();

        Map<String, Object> body = response.getBody();
        assertEquals("https://custom-frontend.com", body.get("frontend_url"));
    }

    @Test
    @DisplayName("Should return provider URL when it points at production host")
    void shouldReturnProductionUrlFromProvider() {
        when(frontendBaseUrlProvider.getBaseUrl()).thenReturn("https://www.prayer-chat.com");

        ResponseEntity<Map<String, Object>> response = rootController.root();

        Map<String, Object> body = response.getBody();
        String frontendUrl = (String) body.get("frontend_url");
        assertTrue(frontendUrl.contains("prayer-chat.com"));
        assertFalse(frontendUrl.contains("localhost"));
    }

    @Test
    @DisplayName("Should return non-localhost URL from provider")
    void shouldReturnNonLocalhostFromProvider() {
        when(frontendBaseUrlProvider.getBaseUrl()).thenReturn("https://production.com");

        ResponseEntity<Map<String, Object>> response = rootController.root();

        Map<String, Object> body = response.getBody();
        String frontendUrl = (String) body.get("frontend_url");
        assertFalse(frontendUrl.contains("localhost"));
        assertFalse(frontendUrl.contains("127.0.0.1"));
        assertTrue(frontendUrl.contains("production.com"));
    }

    @Test
    @DisplayName("Should use default-like URL from provider when configured")
    void shouldUseDefaultLikeUrlFromProvider() {
        when(frontendBaseUrlProvider.getBaseUrl()).thenReturn("https://prayer-chat.com");

        ResponseEntity<Map<String, Object>> response = rootController.root();

        Map<String, Object> body = response.getBody();
        assertEquals("https://prayer-chat.com", body.get("frontend_url"));
    }

    @Test
    @DisplayName("Should not leak sensitive information in response")
    void shouldNotLeakSensitiveInfo() {
        ResponseEntity<Map<String, Object>> response = rootController.root();

        Map<String, Object> body = response.getBody();
        String bodyString = body.toString().toLowerCase();

        assertFalse(bodyString.contains("password"));
        assertFalse(bodyString.contains("secret"));
        assertFalse(bodyString.contains("key"));
        assertFalse(bodyString.contains("token"));
        assertFalse(bodyString.contains("api_key"));
        assertFalse(bodyString.contains("database"));
    }

    @Test
    @DisplayName("Should always include frontend_url when provider returns a value")
    void shouldAlwaysIncludeFrontendUrl() {
        when(frontendBaseUrlProvider.getBaseUrl()).thenReturn("https://fallback.example");

        ResponseEntity<Map<String, Object>> response = rootController.root();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("frontend_url"));
    }
}
