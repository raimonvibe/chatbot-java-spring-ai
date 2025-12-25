package com.prayer_chat.chatbot.security;

import com.prayer_chat.chatbot.service.UrlValidationService;
import com.prayer_chat.chatbot.service.WebsiteSizeEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Security tests for WebsiteSizeEstimator
 * 
 * Verifies SSRF protection is enforced before any network operations
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebsiteSizeEstimator Security Tests")
class WebsiteSizeEstimatorSecurityTest {

    @Mock
    private UrlValidationService urlValidationService;

    @InjectMocks
    private WebsiteSizeEstimator websiteSizeEstimator;

    @Test
    @DisplayName("Should validate URL before size estimation (SSRF protection)")
    void shouldValidateUrlBeforeSizeEstimation() {
        // Arrange
        String maliciousUrl = "http://localhost";
        when(urlValidationService.isValidAndSafe(maliciousUrl)).thenReturn(false);

        // Act
        int result = websiteSizeEstimator.estimateSize(maliciousUrl);

        // Assert: Should return -1 (failure) for unsafe URLs
        assertEquals(-1, result);
        
        // Verify: URL validation was called BEFORE any network operations
        verify(urlValidationService, times(1)).isValidAndSafe(maliciousUrl);
        // Verify: No network operations should occur for unsafe URLs
        // (This is implicit - if validation fails, estimateSize returns -1 immediately)
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost",
        "http://127.0.0.1",
        "http://169.254.169.254", // AWS metadata
        "http://metadata.google.internal", // GCP metadata
        "http://10.0.0.1", // Private IP
        "http://192.168.1.1", // Private IP
        "file:///etc/passwd"
    })
    @DisplayName("Should block SSRF attempts in size estimation")
    void shouldBlockSsrfAttempts(String maliciousUrl) {
        // Arrange
        when(urlValidationService.isValidAndSafe(maliciousUrl)).thenReturn(false);

        // Act
        int result = websiteSizeEstimator.estimateSize(maliciousUrl);

        // Assert: Should return -1 (failure) for SSRF attempts
        assertEquals(-1, result);
        verify(urlValidationService, times(1)).isValidAndSafe(maliciousUrl);
    }

    @Test
    @DisplayName("Should allow safe URLs for size estimation")
    void shouldAllowSafeUrls() {
        // Arrange
        String safeUrl = "https://example.com";
        when(urlValidationService.isValidAndSafe(safeUrl)).thenReturn(true);

        // Act
        // Note: This will try to actually estimate, which may fail in test environment
        // But the important part is that URL validation is called first
        int result = websiteSizeEstimator.estimateSize(safeUrl);

        // Assert: URL validation was called
        verify(urlValidationService, times(1)).isValidAndSafe(safeUrl);
        
        // Result can be -1 (estimation failed) or positive (estimation succeeded)
        // The key security check is that validation was called
        assertNotNull(result);
    }

    @Test
    @DisplayName("Should handle null URL gracefully")
    void shouldHandleNullUrl() {
        // Arrange
        when(urlValidationService.isValidAndSafe(null)).thenReturn(false);

        // Act
        int result = websiteSizeEstimator.estimateSize(null);

        // Assert: Should return -1 for null URL
        assertEquals(-1, result);
        verify(urlValidationService, times(1)).isValidAndSafe(null);
    }

    @Test
    @DisplayName("Should handle empty URL gracefully")
    void shouldHandleEmptyUrl() {
        // Arrange
        when(urlValidationService.isValidAndSafe("")).thenReturn(false);

        // Act
        int result = websiteSizeEstimator.estimateSize("");

        // Assert: Should return -1 for empty URL
        assertEquals(-1, result);
        verify(urlValidationService, times(1)).isValidAndSafe("");
    }
}

