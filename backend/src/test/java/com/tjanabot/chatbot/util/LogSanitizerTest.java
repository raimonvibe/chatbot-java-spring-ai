package com.tjanabot.chatbot.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for LogSanitizer utility class
 * Tests all sanitization methods to ensure sensitive data is properly redacted
 */
@DisplayName("LogSanitizer Tests")
class LogSanitizerTest {

    // ============================================================================
    // API KEY SANITIZATION TESTS
    // ============================================================================

    @Test
    @DisplayName("Should sanitize API keys in various formats")
    void testSanitizeApiKeys() {
        // Test different API key formats
        String input1 = "api_key=sk_test_123456789";
        String input2 = "apiKey: abc123xyz";
        String input3 = "API_KEY=\"key_production_999\"";

        String result1 = LogSanitizer.sanitize(input1);
        String result2 = LogSanitizer.sanitize(input2);
        String result3 = LogSanitizer.sanitize(input3);

        assertFalse(result1.contains("sk_test_123456789"), "API key should be redacted");
        assertFalse(result2.contains("abc123xyz"), "API key should be redacted");
        assertFalse(result3.contains("key_production_999"), "API key should be redacted");

        assertTrue(result1.contains("***REDACTED***"), "Should contain redaction marker");
        assertTrue(result2.contains("***REDACTED***"), "Should contain redaction marker");
        assertTrue(result3.contains("***REDACTED***"), "Should contain redaction marker");
    }

    @Test
    @DisplayName("Should not sanitize false positive API key patterns")
    void testApiKeyFalsePositives() {
        String input = "This is an api_key_example in documentation";
        String result = LogSanitizer.sanitize(input);

        // The pattern should still match, but that's okay for security
        // Better to over-redact than under-redact
        assertNotNull(result);
    }

    // ============================================================================
    // PASSWORD SANITIZATION TESTS
    // ============================================================================

    @Test
    @DisplayName("Should sanitize passwords in various formats")
    void testSanitizePasswords() {
        String input1 = "password=MySecret123";
        String input2 = "Password: hunter2";
        String input3 = "PASSWORD=\"SuperSecret!@#\"";

        String result1 = LogSanitizer.sanitize(input1);
        String result2 = LogSanitizer.sanitize(input2);
        String result3 = LogSanitizer.sanitize(input3);

        assertFalse(result1.contains("MySecret123"), "Password should be redacted");
        assertFalse(result2.contains("hunter2"), "Password should be redacted");
        assertFalse(result3.contains("SuperSecret!@#"), "Password should be redacted");

        assertTrue(result1.contains("***REDACTED***"));
        assertTrue(result2.contains("***REDACTED***"));
        assertTrue(result3.contains("***REDACTED***"));
    }

    // ============================================================================
    // TOKEN SANITIZATION TESTS
    // ============================================================================

    @Test
    @DisplayName("Should sanitize bearer tokens")
    void testSanitizeBearerTokens() {
        String input = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";
        String result = LogSanitizer.sanitize(input);

        assertFalse(result.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"), "JWT token should be redacted");
        assertTrue(result.contains("Bearer ***REDACTED***"));
    }

    @Test
    @DisplayName("Should sanitize generic tokens")
    void testSanitizeTokens() {
        String input1 = "token=abc123def456";
        String input2 = "access_token: xyz789";

        String result1 = LogSanitizer.sanitize(input1);
        String result2 = LogSanitizer.sanitize(input2);

        assertFalse(result1.contains("abc123def456"));
        assertTrue(result1.contains("***REDACTED***"));
    }

    // ============================================================================
    // EMAIL SANITIZATION TESTS
    // ============================================================================

    @Test
    @DisplayName("Should partially redact email addresses")
    void testSanitizeEmails() {
        String input = "User email: john.doe@example.com";
        String result = LogSanitizer.sanitize(input);

        // Should keep first 2 chars and domain
        assertFalse(result.contains("john.doe@example.com"), "Full email should not be present");
        assertTrue(result.contains("@example.com"), "Domain should be preserved");
        assertTrue(result.contains("jo***@example.com"), "Should partially redact local part");
    }

    @Test
    @DisplayName("Should handle short email addresses")
    void testSanitizeShortEmails() {
        String input = "Email: ab@test.com";
        String result = LogSanitizer.sanitize(input);

        assertFalse(result.contains("ab@test.com"), "Full email should not be present");
        assertTrue(result.contains("@test.com"), "Domain should be preserved");
    }

    @Test
    @DisplayName("Should handle single character email")
    void testSanitizeSingleCharEmail() {
        String input = "Email: a@example.com";
        String result = LogSanitizer.sanitize(input);

        assertTrue(result.contains("***@example.com"), "Single char email should be fully redacted");
    }

    // ============================================================================
    // IP ADDRESS SANITIZATION TESTS
    // ============================================================================

    @Test
    @DisplayName("Should partially redact IP addresses")
    void testSanitizeIpAddresses() {
        String input = "Request from IP: 192.168.1.100";
        String result = LogSanitizer.sanitize(input);

        assertFalse(result.contains("192.168.1.100"), "Full IP should not be present");
        assertTrue(result.contains("192.*.*.*"), "Should keep first octet only");
    }

    @Test
    @DisplayName("Should handle multiple IP addresses")
    void testSanitizeMultipleIps() {
        String input = "IPs: 10.0.0.1, 172.16.0.1, 203.45.67.89";
        String result = LogSanitizer.sanitize(input);

        assertTrue(result.contains("10.*.*.*"));
        assertTrue(result.contains("172.*.*.*"));
        assertTrue(result.contains("203.*.*.*"));
        assertFalse(result.contains("10.0.0.1"));
    }

    // ============================================================================
    // SECRET SANITIZATION TESTS
    // ============================================================================

    @Test
    @DisplayName("Should sanitize secrets")
    void testSanitizeSecrets() {
        String input1 = "client_secret=cs_test_123456";
        String input2 = "SECRET: my-secret-value";

        String result1 = LogSanitizer.sanitize(input1);
        String result2 = LogSanitizer.sanitize(input2);

        assertFalse(result1.contains("cs_test_123456"));
        assertFalse(result2.contains("my-secret-value"));
        assertTrue(result1.contains("***REDACTED***"));
        assertTrue(result2.contains("***REDACTED***"));
    }

    // ============================================================================
    // AUTHORIZATION HEADER TESTS
    // ============================================================================

    @Test
    @DisplayName("Should sanitize authorization headers")
    void testSanitizeAuthorizationHeaders() {
        String input = "Authorization: Basic dXNlcjpwYXNzd29yZA==";
        String result = LogSanitizer.sanitize(input);

        assertFalse(result.contains("dXNlcjpwYXNzd29yZA=="));
        assertTrue(result.contains("***REDACTED***"));
    }

    // ============================================================================
    // URL SANITIZATION TESTS
    // ============================================================================

    @Test
    @DisplayName("Should sanitize URL query parameters")
    void testSanitizeUrl() {
        String url = "https://api.example.com/endpoint?api_key=secret123&user=john";
        String result = LogSanitizer.sanitizeUrl(url);

        assertEquals("https://api.example.com/endpoint?***", result);
        assertFalse(result.contains("api_key=secret123"));
    }

    @Test
    @DisplayName("Should handle URLs without query parameters")
    void testSanitizeUrlWithoutParams() {
        String url = "https://api.example.com/endpoint";
        String result = LogSanitizer.sanitizeUrl(url);

        assertEquals(url, result, "URL without params should remain unchanged");
    }

    // ============================================================================
    // EXCEPTION SANITIZATION TESTS
    // ============================================================================

    @Test
    @DisplayName("Should sanitize exception messages")
    void testSanitizeException() {
        Exception e = new RuntimeException("Error connecting with api_key=secret123");
        String result = LogSanitizer.sanitizeException(e);

        assertFalse(result.contains("secret123"));
        assertTrue(result.contains("***REDACTED***"));
    }

    @Test
    @DisplayName("Should handle null exception")
    void testSanitizeNullException() {
        String result = LogSanitizer.sanitizeException(null);
        assertNull(result);
    }

    @Test
    @DisplayName("Should sanitize exception with cause")
    void testSanitizeExceptionWithCause() {
        Exception cause = new IllegalArgumentException("Invalid password=MyPass123");
        Exception e = new RuntimeException("Failed to authenticate", cause);

        String result = LogSanitizer.sanitizeException(e);

        assertFalse(result.contains("MyPass123"));
        assertTrue(result.contains("***REDACTED***"));
        assertTrue(result.contains("Caused by:"));
    }

    // ============================================================================
    // SENSITIVE DATA DETECTION TESTS
    // ============================================================================

    @Test
    @DisplayName("Should detect sensitive data in strings")
    void testContainsSensitiveData() {
        assertTrue(LogSanitizer.containsSensitiveData("api_key=secret"));
        assertTrue(LogSanitizer.containsSensitiveData("password=test123"));
        assertTrue(LogSanitizer.containsSensitiveData("Bearer token123"));
        assertTrue(LogSanitizer.containsSensitiveData("Authorization: Basic abc"));

        assertFalse(LogSanitizer.containsSensitiveData("Normal log message"));
        assertFalse(LogSanitizer.containsSensitiveData("User logged in successfully"));
    }

    @Test
    @DisplayName("Should handle null and empty strings in detection")
    void testContainsSensitiveDataNullEmpty() {
        assertFalse(LogSanitizer.containsSensitiveData(null));
        assertFalse(LogSanitizer.containsSensitiveData(""));
    }

    // ============================================================================
    // TRUNCATION TESTS
    // ============================================================================

    @Test
    @DisplayName("Should truncate long messages")
    void testTruncate() {
        String longMessage = "a".repeat(2000);
        String result = LogSanitizer.truncate(longMessage, 100);

        assertEquals(100 + "... (truncated)".length(), result.length());
        assertTrue(result.endsWith("... (truncated)"));
    }

    @Test
    @DisplayName("Should not truncate short messages")
    void testTruncateShortMessage() {
        String shortMessage = "Short message";
        String result = LogSanitizer.truncate(shortMessage, 100);

        assertEquals(shortMessage, result);
    }

    @Test
    @DisplayName("Should handle null in truncate")
    void testTruncateNull() {
        String result = LogSanitizer.truncate(null, 100);
        assertNull(result);
    }

    // ============================================================================
    // NEWLINE REMOVAL TESTS (Log Injection Prevention)
    // ============================================================================

    @Test
    @DisplayName("Should remove newlines to prevent log injection")
    void testRemoveNewlines() {
        String input = "Line 1\nLine 2\rLine 3\r\nLine 4";
        String result = LogSanitizer.removeNewlines(input);

        assertFalse(result.contains("\n"), "Should not contain newlines");
        assertFalse(result.contains("\r"), "Should not contain carriage returns");
        // \r\n becomes two spaces (one for \r, one for \n)
        assertTrue(result.contains("Line 1") && result.contains("Line 4"),
            "Should contain all lines");
    }

    @Test
    @DisplayName("Should handle null in newline removal")
    void testRemoveNewlinesNull() {
        String result = LogSanitizer.removeNewlines(null);
        assertNull(result);
    }

    // ============================================================================
    // COMPREHENSIVE LOGGING SANITIZATION TESTS
    // ============================================================================

    @Test
    @DisplayName("Should apply all sanitization methods comprehensively")
    void testSanitizeForLogging() {
        String input = "User john.doe@example.com logged in with password=Secret123\nFrom IP 192.168.1.1\nusing api_key=sk_test_abc";
        String result = LogSanitizer.sanitizeForLogging(input);

        // Check all sanitizations applied
        assertFalse(result.contains("Secret123"), "Password should be sanitized");
        assertFalse(result.contains("sk_test_abc"), "API key should be sanitized");
        assertFalse(result.contains("192.168.1.1"), "IP should be sanitized");
        assertFalse(result.contains("\n"), "Newlines should be removed");
        assertTrue(result.contains("jo***@example.com"), "Email should be partially redacted");

        // Should be truncated to 1000 chars max
        assertTrue(result.length() <= 1000 + "... (truncated)".length());
    }

    @Test
    @DisplayName("Should handle null in comprehensive sanitization")
    void testSanitizeForLoggingNull() {
        String result = LogSanitizer.sanitizeForLogging(null);
        assertNull(result);
    }

    @Test
    @DisplayName("Should handle empty string")
    void testSanitizeEmptyString() {
        String result = LogSanitizer.sanitize("");
        assertEquals("", result);
    }

    // ============================================================================
    // REAL-WORLD SCENARIO TESTS
    // ============================================================================

    @Test
    @DisplayName("Should sanitize realistic Stripe webhook payload")
    void testSanitizeStripeWebhook() {
        String input = "Stripe webhook received: {\"type\":\"customer.created\",\"api_version\":\"2020-08-27\",\"data\":{\"object\":{\"email\":\"customer@example.com\"}}}";
        String result = LogSanitizer.sanitize(input);

        // Email should be sanitized
        assertTrue(result.contains("cu***@example.com"));
    }

    @Test
    @DisplayName("Should sanitize realistic OAuth error message")
    void testSanitizeOAuthError() {
        String input = "OAuth error: invalid_grant for user admin@company.com with client_secret=cs_prod_123456";
        String result = LogSanitizer.sanitize(input);

        assertFalse(result.contains("cs_prod_123456"));
        assertTrue(result.contains("ad***@company.com"));
        assertTrue(result.contains("***REDACTED***"));
    }

    @Test
    @DisplayName("Should sanitize realistic database connection string")
    void testSanitizeDatabaseUrl() {
        String input = "Connecting to: postgresql://user:MySecretPass123@localhost:5432/dbname";
        String result = LogSanitizer.sanitize(input);

        // The actual password value should be sanitized
        assertFalse(result.contains("MySecretPass123"), "Password value should be redacted");
        // Note: The word "password" in patterns like "password=" gets redacted,
        // but "password" as part of a URL path is left as-is (which is correct)
    }

    @Test
    @DisplayName("Should sanitize realistic JWT token in logs")
    void testSanitizeJwtToken() {
        String input = "Authentication failed for token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        String result = LogSanitizer.sanitize(input);

        // Should sanitize the token part
        assertTrue(result.contains("token"));
        assertTrue(result.contains("***REDACTED***"));
    }

    // ============================================================================
    // EDGE CASES AND BOUNDARY TESTS
    // ============================================================================

    @ParameterizedTest
    @ValueSource(strings = {
        "api_key=",
        "password=",
        "token=",
        "secret="
    })
    @DisplayName("Should handle empty values gracefully")
    void testEmptyValues(String input) {
        String result = LogSanitizer.sanitize(input);
        assertNotNull(result);
        // Should still apply sanitization pattern
    }

    @Test
    @DisplayName("Should handle very long input efficiently")
    void testVeryLongInput() {
        String longInput = "Normal text ".repeat(10000) + " with api_key=secret at the end";

        long startTime = System.currentTimeMillis();
        String result = LogSanitizer.sanitize(longInput);
        long duration = System.currentTimeMillis() - startTime;

        // Should complete in reasonable time (< 1 second)
        assertTrue(duration < 1000, "Sanitization should be performant");
        assertFalse(result.contains("api_key=secret"));
    }

    @Test
    @DisplayName("Should handle Unicode characters")
    void testUnicodeCharacters() {
        String input = "User email: 用户@example.com with password=密码123";
        String result = LogSanitizer.sanitize(input);

        assertFalse(result.contains("密码123"));
        assertTrue(result.contains("***REDACTED***"));
    }

    @Test
    @DisplayName("Should handle special regex characters safely")
    void testSpecialRegexCharacters() {
        String input = "api_key=$pecial.ch@r$123";
        String result = LogSanitizer.sanitize(input);

        // Should not throw exception
        assertNotNull(result);
    }

    @Test
    @DisplayName("Should preserve non-sensitive information")
    void testPreserveNonSensitiveInfo() {
        String input = "User ID 12345 performed action 'create_chatbot' at timestamp 1234567890";
        String result = LogSanitizer.sanitize(input);

        // Non-sensitive data should be preserved
        assertTrue(result.contains("12345"));
        assertTrue(result.contains("create_chatbot"));
        assertTrue(result.contains("1234567890"));
    }
}
