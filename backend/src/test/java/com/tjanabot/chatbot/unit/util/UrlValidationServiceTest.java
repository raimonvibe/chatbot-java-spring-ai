package com.tjanabot.chatbot.unit.util;

import com.tjanabot.chatbot.service.UrlValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("UrlValidationService Unit Tests")
class UrlValidationServiceTest {

    private UrlValidationService validator;

    @BeforeEach
    void setUp() {
        validator = new UrlValidationService();
    }

    @Test
    @DisplayName("Should accept valid public URLs")
    void shouldAcceptValidPublicUrls() {
        assertThat(validator.isValid("https://example.com")).isTrue();
        assertThat(validator.isValid("http://google.com")).isTrue();
        assertThat(validator.isValid("https://www.github.com")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost",
        "http://localhost:8080",
        "http://127.0.0.1",
        "http://127.0.0.1:3000",
        "http://[::1]",
        "http://[::1]:8080"
    })
    @DisplayName("Should reject localhost addresses")
    void shouldRejectLocalhostAddresses(String url) {
        assertThat(validator.isValid(url))
            .as("URL %s should be rejected", url)
            .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://10.0.0.1",
        "http://10.255.255.255",
        "http://192.168.1.1",
        "http://192.168.0.100",
        "http://172.16.0.1",
        "http://172.31.255.255"
    })
    @DisplayName("Should reject private IP ranges")
    void shouldRejectPrivateIpRanges(String url) {
        assertThat(validator.isValid(url))
            .as("Private IP %s should be rejected", url)
            .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://169.254.169.254",  // AWS metadata
        "http://169.254.169.254/latest/meta-data/",
        "http://metadata.google.internal",  // GCP metadata
        "http://169.254.169.254/metadata/instance"  // Azure metadata
    })
    @DisplayName("Should reject cloud metadata endpoints")
    void shouldRejectCloudMetadataEndpoints(String url) {
        assertThat(validator.isValid(url))
            .as("Metadata endpoint %s should be rejected", url)
            .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ftp://example.com",
        "file:///etc/passwd",
        "gopher://example.com",
        "javascript:alert(1)"
    })
    @DisplayName("Should reject non-HTTP protocols")
    void shouldRejectNonHttpProtocols(String url) {
        assertThat(validator.isValid(url))
            .as("Non-HTTP protocol %s should be rejected", url)
            .isFalse();
    }

    @Test
    @DisplayName("Should reject null URL")
    void shouldRejectNullUrl() {
        assertThat(validator.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("Should reject empty URL")
    void shouldRejectEmptyUrl() {
        assertThat(validator.isValid("")).isFalse();
    }

    @Test
    @DisplayName("Should reject malformed URL")
    void shouldRejectMalformedUrl() {
        assertThat(validator.isValid("not-a-url")).isFalse();
        assertThat(validator.isValid("ht tp://example.com")).isFalse();
    }
}
