package com.prayer_chat.chatbot.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebsiteSizeEstimator Tests")
class WebsiteSizeEstimatorTest {

    @Mock
    private UrlValidationService urlValidationService;

    @InjectMocks
    private WebsiteSizeEstimator websiteSizeEstimator;

    @BeforeEach
    void setUp() {
        // Default: allow all URLs for testing (unless test specifies otherwise)
        when(urlValidationService.isValidAndSafe(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("Should return conservative estimate when estimation fails")
    void shouldReturnConservativeEstimateWhenEstimationFails() {
        // Arrange - Invalid URL that will fail all estimation methods
        String invalidUrl = "https://invalid-domain-that-does-not-exist-12345.com";

        // Act
        int estimate = websiteSizeEstimator.estimateSize(invalidUrl);

        // Assert - Should return conservative default (10)
        assertThat(estimate).isEqualTo(10);
    }

    @Test
    @DisplayName("Should return positive estimate for valid URL")
    void shouldReturnPositiveEstimateForValidUrl() {
        // Arrange - Use a real website URL (will use fallback methods)
        String validUrl = "https://example.com";

        // Act
        int estimate = websiteSizeEstimator.estimateSize(validUrl);

        // Assert - Should return a positive estimate (either from estimation or default)
        assertThat(estimate).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should handle URLs with different protocols")
    void shouldHandleUrlsWithDifferentProtocols() {
        // Act & Assert - Should not throw exceptions
        int estimate1 = websiteSizeEstimator.estimateSize("http://example.com");
        int estimate2 = websiteSizeEstimator.estimateSize("https://example.com");

        assertThat(estimate1).isGreaterThan(0);
        assertThat(estimate2).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should handle malformed URLs gracefully")
    void shouldHandleMalformedUrlsGracefully() {
        // Act & Assert - Should not throw exceptions, return default
        int estimate = websiteSizeEstimator.estimateSize("not-a-valid-url");

        assertThat(estimate).isGreaterThanOrEqualTo(10); // Default fallback
    }

    @Test
    @DisplayName("Should return estimate for localhost URLs")
    void shouldReturnEstimateForLocalhostUrls() {
        // Act & Assert - Should handle localhost (though may fail, should not throw)
        int estimate = websiteSizeEstimator.estimateSize("http://localhost:8080");

        assertThat(estimate).isGreaterThanOrEqualTo(10); // Default fallback
    }
}

