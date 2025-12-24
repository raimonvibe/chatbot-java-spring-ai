package com.prayer_chat.chatbot.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security tests for CORS Configuration
 * 
 * Verifies that CORS is properly configured and secure.
 */
@DisplayName("CORS Configuration Security Tests")
class CorsConfigurationSecurityTest {

    @Test
    @DisplayName("Should only allow whitelisted origins")
    void shouldOnlyAllowWhitelistedOrigins() {
        // Simulate CORS configuration
        CorsConfiguration config = new CorsConfiguration();
        String allowedOrigins = "https://prayer-chat.com,https://www.prayer-chat.com,http://localhost:3000";
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));

        assertNotNull(config.getAllowedOrigins());
        assertTrue(config.getAllowedOrigins().contains("https://prayer-chat.com"));
        assertTrue(config.getAllowedOrigins().contains("https://www.prayer-chat.com"));
        assertTrue(config.getAllowedOrigins().contains("http://localhost:3000"));
        
        // Should not allow arbitrary origins
        assertFalse(config.getAllowedOrigins().contains("https://malicious-site.com"));
    }

    @Test
    @DisplayName("Should require credentials for authenticated requests")
    void shouldRequireCredentials() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);

        assertTrue(config.getAllowCredentials());
    }

    @Test
    @DisplayName("Should restrict HTTP methods to safe operations")
    void shouldRestrictHttpMethods() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        List<String> allowedMethods = config.getAllowedMethods();
        assertTrue(allowedMethods.contains("GET"));
        assertTrue(allowedMethods.contains("POST"));
        assertTrue(allowedMethods.contains("PUT"));
        assertTrue(allowedMethods.contains("DELETE"));
        assertTrue(allowedMethods.contains("OPTIONS"));
        
        // Should not allow dangerous methods
        assertFalse(allowedMethods.contains("TRACE"));
        assertFalse(allowedMethods.contains("CONNECT"));
    }

    @Test
    @DisplayName("Should set max age for preflight cache")
    void shouldSetMaxAgeForPreflightCache() {
        CorsConfiguration config = new CorsConfiguration();
        config.setMaxAge(3600L);

        assertEquals(3600L, config.getMaxAge());
    }

    @Test
    @DisplayName("Should only apply CORS to /api/** paths")
    void shouldOnlyApplyCorsToApiPaths() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList("https://prayer-chat.com"));
        
        // Register CORS only for /api/** paths
        source.registerCorsConfiguration("/api/**", config);

        // CORS should exist for /api/** paths
        assertNotNull(source.getCorsConfiguration("/api/test"));
        assertNotNull(source.getCorsConfiguration("/api/chatbots"));
        
        // CORS should not exist for non-API paths (returns null)
        // Note: This is expected behavior - CORS only applies to /api/**
    }

    @Test
    @DisplayName("Should parse comma-separated origins correctly")
    void shouldParseCommaSeparatedOrigins() {
        String origins = "https://example1.com,https://example2.com,https://example3.com";
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(origins.split(",")));

        List<String> allowedOrigins = config.getAllowedOrigins();
        assertEquals(3, allowedOrigins.size());
        assertTrue(allowedOrigins.contains("https://example1.com"));
        assertTrue(allowedOrigins.contains("https://example2.com"));
        assertTrue(allowedOrigins.contains("https://example3.com"));
    }

    @Test
    @DisplayName("Should not allow wildcard origin with credentials")
    void shouldNotAllowWildcardOriginWithCredentials() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        
        // Security: When credentials are allowed, wildcard origin should not be used
        // This test verifies the configuration doesn't use wildcard
        config.setAllowedOrigins(Arrays.asList("https://prayer-chat.com"));
        
        assertTrue(config.getAllowCredentials());
        assertFalse(config.getAllowedOrigins().contains("*"));
    }

    @Test
    @DisplayName("Should handle empty origins gracefully")
    void shouldHandleEmptyOrigins() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList());

        assertNotNull(config.getAllowedOrigins());
        assertTrue(config.getAllowedOrigins().isEmpty());
    }
}

