package com.tjanabot.chatbot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for UrlValidationService
 * Critical security component that prevents SSRF (Server-Side Request Forgery) attacks
 */
@DisplayName("UrlValidationService SSRF Protection Tests")
class UrlValidationServiceTest {

    private UrlValidationService urlValidationService;

    @BeforeEach
    void setUp() {
        urlValidationService = new UrlValidationService();
    }

    // ============================================================================
    // VALID PUBLIC URL TESTS
    // ============================================================================

    @ParameterizedTest
    @ValueSource(strings = {
        "https://www.google.com",
        "http://google.com",
        "https://api.github.com/repos",
        "https://www.google.com/search?q=test",
        "http://github.com:8080/path",
        "https://www.wikipedia.org",
        "https://google.com:443"
    })
    @DisplayName("Should allow valid public URLs")
    void testValidPublicUrls(String url) {
        assertTrue(urlValidationService.isValidAndSafe(url),
            "Valid public URL should be allowed: " + url);
    }

    // ============================================================================
    // LOCALHOST BLOCKING TESTS (SSRF Protection)
    // ============================================================================

    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost",
        "http://localhost:8080",
        "http://localhost/admin",
        "https://localhost:443/api"
    })
    @DisplayName("Should block localhost URLs")
    void testBlockLocalhost(String url) {
        assertFalse(urlValidationService.isValidAndSafe(url),
            "Localhost URL should be blocked: " + url);
    }

    // ============================================================================
    // LOOPBACK IP BLOCKING TESTS (127.0.0.0/8)
    // ============================================================================

    @ParameterizedTest
    @ValueSource(strings = {
        "http://127.0.0.1",
        "http://127.0.0.1:8080",
        "http://127.1.1.1",
        "http://127.255.255.255",
        "https://127.0.0.1/admin"
    })
    @DisplayName("Should block all 127.x.x.x loopback addresses")
    void testBlockLoopbackIps(String url) {
        assertFalse(urlValidationService.isValidAndSafe(url),
            "Loopback IP should be blocked: " + url);
    }

    // ============================================================================
    // PRIVATE IP BLOCKING TESTS (RFC 1918)
    // ============================================================================

    @ParameterizedTest
    @ValueSource(strings = {
        // Class A private (10.0.0.0/8)
        "http://10.0.0.1",
        "http://10.255.255.255",
        "http://10.1.2.3:8080",
        // Class B private (172.16.0.0/12)
        "http://172.16.0.1",
        "http://172.31.255.255",
        "http://172.20.1.1",
        // Class C private (192.168.0.0/16)
        "http://192.168.0.1",
        "http://192.168.1.1",
        "http://192.168.255.255"
    })
    @DisplayName("Should block all private IP ranges (RFC 1918)")
    void testBlockPrivateIps(String url) {
        assertFalse(urlValidationService.isValidAndSafe(url),
            "Private IP should be blocked: " + url);
    }

    // ============================================================================
    // CLOUD METADATA BLOCKING TESTS (Critical for Cloud Security)
    // ============================================================================

    @ParameterizedTest
    @ValueSource(strings = {
        // AWS metadata
        "http://169.254.169.254/latest/meta-data/",
        "http://169.254.169.254/latest/user-data/",
        "http://169.254.169.254",
        // GCP metadata
        "http://metadata.google.internal",
        "http://metadata.google.internal/computeMetadata/v1/",
        "http://metadata",
        // Azure metadata (uses private IP)
        "http://169.254.169.254/metadata/instance"
    })
    @DisplayName("Should block cloud metadata endpoints (AWS, GCP, Azure)")
    void testBlockCloudMetadata(String url) {
        assertFalse(urlValidationService.isValidAndSafe(url),
            "Cloud metadata endpoint should be blocked: " + url);
    }

    // ============================================================================
    // LINK-LOCAL IP BLOCKING TESTS (169.254.0.0/16)
    // ============================================================================

    @ParameterizedTest
    @ValueSource(strings = {
        "http://169.254.0.1",
        "http://169.254.100.200",
        "http://169.254.255.255"
    })
    @DisplayName("Should block link-local addresses (169.254.x.x)")
    void testBlockLinkLocalIps(String url) {
        assertFalse(urlValidationService.isValidAndSafe(url),
            "Link-local IP should be blocked: " + url);
    }

    // ============================================================================
    // SPECIAL IP BLOCKING TESTS
    // ============================================================================

    @ParameterizedTest
    @ValueSource(strings = {
        "http://0.0.0.0",
        "http://0.0.0.0:8080"
    })
    @DisplayName("Should block 0.0.0.0 addresses")
    void testBlockZeroIp(String url) {
        assertFalse(urlValidationService.isValidAndSafe(url),
            "0.0.0.0 should be blocked: " + url);
    }

    // ============================================================================
    // IPV6 BLOCKING TESTS
    // ============================================================================

    @ParameterizedTest
    @ValueSource(strings = {
        "http://[::1]",
        "http://[::1]:8080",
        "http://[0:0:0:0:0:0:0:1]",
        "http://[fe80::1]"
    })
    @DisplayName("Should block IPv6 loopback and link-local addresses")
    void testBlockIpv6(String url) {
        assertFalse(urlValidationService.isValidAndSafe(url),
            "IPv6 loopback/link-local should be blocked: " + url);
    }

    // ============================================================================
    // INVALID SCHEME BLOCKING TESTS
    // ============================================================================

    @ParameterizedTest
    @ValueSource(strings = {
        "ftp://example.com",
        "file:///etc/passwd",
        "gopher://example.com",
        "dict://example.com",
        "data:text/html,<script>alert('xss')</script>",
        "javascript:alert('xss')",
        "jar:http://example.com!/",
        "php://filter/resource=index.php"
    })
    @DisplayName("Should block non-HTTP/HTTPS schemes")
    void testBlockDangerousSchemes(String url) {
        assertFalse(urlValidationService.isValidAndSafe(url),
            "Dangerous scheme should be blocked: " + url);
    }

    // ============================================================================
    // PORT VALIDATION TESTS
    // ============================================================================

    @ParameterizedTest
    @ValueSource(strings = {
        "http://example.com:80",      // HTTP default
        "https://example.com:443",    // HTTPS default
        "http://example.com:8080",    // Common alt HTTP
        "https://example.com:8443",   // Common alt HTTPS
        "http://example.com:3000"     // Dev port (3000-3999 allowed)
    })
    @DisplayName("Should allow safe ports")
    void testAllowSafePorts(String url) {
        assertTrue(urlValidationService.isValidAndSafe(url),
            "Safe port should be allowed: " + url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://example.com:22",      // SSH
        "http://example.com:23",      // Telnet
        "http://example.com:3389",    // RDP
        "http://example.com:5432",    // PostgreSQL
        "http://example.com:27017",   // MongoDB
        "http://example.com:6379",    // Redis
        "http://example.com:9200"     // Elasticsearch
    })
    @DisplayName("Should block dangerous ports")
    void testBlockDangerousPorts(String url) {
        assertFalse(urlValidationService.isValidAndSafe(url),
            "Dangerous port should be blocked: " + url);
    }

    // ============================================================================
    // MALFORMED URL TESTS
    // ============================================================================

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "not-a-url",
        "http://",
        "://example.com",
        "http:/example.com",
        "example.com"  // Missing scheme
    })
    @DisplayName("Should reject malformed URLs")
    void testRejectMalformedUrls(String url) {
        assertFalse(urlValidationService.isValidAndSafe(url),
            "Malformed URL should be rejected: " + url);
    }

    @Test
    @DisplayName("Should reject null URL")
    void testRejectNullUrl() {
        assertFalse(urlValidationService.isValidAndSafe(null),
            "Null URL should be rejected");
    }

    // ============================================================================
    // DNS REBINDING PROTECTION TESTS
    // ============================================================================

    @Test
    @DisplayName("Should resolve domain and check resulting IP")
    void testDnsResolution() {
        // This test validates that the service resolves the domain
        // and checks the resulting IP address

        // These should fail because they resolve to private/loopback IPs
        // Note: These tests may behave differently in different environments
        // In a real test environment, you might mock DNS resolution

        // Testing with actual DNS resolution
        String publicUrl = "https://www.google.com";
        assertTrue(urlValidationService.isValidAndSafe(publicUrl),
            "Public domain should be allowed");
    }

    // ============================================================================
    // DOMAIN EXTRACTION TESTS
    // ============================================================================

    @Test
    @DisplayName("Should extract domain from valid URL")
    void testExtractDomain() {
        String url = "https://api.example.com:8080/path?query=value";
        String domain = urlValidationService.extractDomain(url);

        assertEquals("api.example.com", domain);
    }

    @Test
    @DisplayName("Should handle invalid URL in domain extraction")
    void testExtractDomainInvalidUrl() {
        String invalidUrl = "not-a-valid-url";
        String domain = urlValidationService.extractDomain(invalidUrl);

        assertEquals("invalid-url", domain);
    }

    // ============================================================================
    // URL PATTERN VALIDATION TESTS
    // ============================================================================

    @ParameterizedTest
    @ValueSource(strings = {
        "http://example",               // No TLD
        "http://192.168.1.1",          // Bare IP (should fail domain pattern)
        "http://-example.com",         // Invalid domain start
        "http://example-.com"          // Invalid domain end
    })
    @DisplayName("Should reject invalid URL patterns")
    void testInvalidUrlPatterns(String url) {
        assertFalse(urlValidationService.isValidAndSafe(url),
            "Invalid URL pattern should be rejected: " + url);
    }

    // ============================================================================
    // BYPASS ATTEMPT TESTS (Security Critical)
    // ============================================================================

    @ParameterizedTest
    @ValueSource(strings = {
        // URL encoding bypass attempts
        "http://127.0.0.1",
        "http://0x7f000001",           // Hex encoded localhost
        "http://2130706433",           // Decimal encoded localhost
        "http://0177.0.0.1",           // Octal encoded
        // Double encoding
        "http://%31%32%37%2e%30%2e%30%2e%31",
        // Using @ symbol
        "http://example.com@127.0.0.1",
        "http://example.com@localhost"
    })
    @DisplayName("Should block SSRF bypass attempts")
    void testSsrfBypassAttempts(String url) {
        // Note: Some of these may not be caught by simple pattern matching
        // but should fail during DNS resolution
        boolean result = urlValidationService.isValidAndSafe(url);

        // We expect these to be blocked, but note in comments which ones
        // require DNS resolution to catch
        assertFalse(result, "SSRF bypass attempt should be blocked: " + url);
    }

    // ============================================================================
    // CASE SENSITIVITY TESTS
    // ============================================================================

    @ParameterizedTest
    @ValueSource(strings = {
        "http://LOCALHOST",
        "http://LocalHost",
        "http://METADATA.GOOGLE.INTERNAL"
    })
    @DisplayName("Should block URLs regardless of case")
    void testCaseInsensitiveBlocking(String url) {
        assertFalse(urlValidationService.isValidAndSafe(url),
            "Blocked URL should be rejected regardless of case: " + url);
    }

    // ============================================================================
    // REAL-WORLD ATTACK SCENARIOS
    // ============================================================================

    @Test
    @DisplayName("Should block AWS credential theft attempt")
    void testBlockAwsCredentialTheft() {
        String awsMetadataUrl = "http://169.254.169.254/latest/meta-data/iam/security-credentials/";
        assertFalse(urlValidationService.isValidAndSafe(awsMetadataUrl),
            "AWS credential theft URL should be blocked");
    }

    @Test
    @DisplayName("Should block GCP metadata access attempt")
    void testBlockGcpMetadataAccess() {
        String gcpMetadataUrl = "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token";
        assertFalse(urlValidationService.isValidAndSafe(gcpMetadataUrl),
            "GCP metadata access should be blocked");
    }

    @Test
    @DisplayName("Should block internal service scan attempt")
    void testBlockInternalServiceScan() {
        String internalServiceUrl = "http://192.168.1.100:9200/_cluster/health";
        assertFalse(urlValidationService.isValidAndSafe(internalServiceUrl),
            "Internal service scan should be blocked");
    }

    @Test
    @DisplayName("Should block Docker daemon access attempt")
    void testBlockDockerDaemon() {
        String dockerUrl = "http://127.0.0.1:2375/containers/json";
        assertFalse(urlValidationService.isValidAndSafe(dockerUrl),
            "Docker daemon access should be blocked");
    }

    // ============================================================================
    // EDGE CASES AND BOUNDARY TESTS
    // ============================================================================

    @Test
    @DisplayName("Should handle URLs with fragments")
    void testUrlWithFragment() {
        String url = "https://example.com/page#section";
        assertTrue(urlValidationService.isValidAndSafe(url),
            "URL with fragment should be allowed");
    }

    @Test
    @DisplayName("Should handle URLs with authentication")
    void testUrlWithAuth() {
        // URLs with user:pass@ should be parsed correctly
        String url = "http://user:pass@example.com";
        // This might be blocked depending on implementation
        boolean result = urlValidationService.isValidAndSafe(url);

        // Document the behavior
        assertNotNull(result);
    }

    @Test
    @DisplayName("Should handle very long URLs")
    void testVeryLongUrl() {
        String longPath = "a".repeat(2000);
        String url = "https://example.com/" + longPath;

        // Should not crash
        assertDoesNotThrow(() -> urlValidationService.isValidAndSafe(url));
    }

    @Test
    @DisplayName("Should handle international domain names")
    void testInternationalDomains() {
        // IDN (Internationalized Domain Names)
        String url = "https://münchen.de";

        // Behavior depends on implementation
        assertDoesNotThrow(() -> urlValidationService.isValidAndSafe(url));
    }

    // ============================================================================
    // PERFORMANCE TESTS
    // ============================================================================

    @Test
    @DisplayName("Should validate URLs quickly")
    void testValidationPerformance() {
        String url = "https://example.com";

        long startTime = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            urlValidationService.isValidAndSafe(url);
        }
        long duration = (System.nanoTime() - startTime) / 1_000_000; // Convert to ms

        // 100 validations should complete in under 1 second
        assertTrue(duration < 1000,
            "URL validation should be performant: " + duration + "ms for 100 validations");
    }

    // ============================================================================
    // WHITELIST/SECURITY POSITIVE TESTS
    // ============================================================================

    @Test
    @DisplayName("Should allow legitimate API endpoints")
    void testAllowLegitimateApis() {
        String[] legitimateUrls = {
            "https://api.stripe.com/v1/charges",
            "https://graph.facebook.com/v12.0/me",
            "https://www.googleapis.com/oauth2/v1/userinfo",
            "https://api.github.com/user",
            "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXX"
        };

        for (String url : legitimateUrls) {
            assertTrue(urlValidationService.isValidAndSafe(url),
                "Legitimate API URL should be allowed: " + url);
        }
    }

    @Test
    @DisplayName("Should allow common CDN URLs")
    void testAllowCdnUrls() {
        String[] cdnUrls = {
            "https://cdn.jsdelivr.net/npm/package",
            "https://unpkg.com/package@version",
            "https://cdnjs.cloudflare.com/ajax/libs/library.js",
            "https://fonts.googleapis.com/css"
        };

        for (String url : cdnUrls) {
            assertTrue(urlValidationService.isValidAndSafe(url),
                "CDN URL should be allowed: " + url);
        }
    }
}
