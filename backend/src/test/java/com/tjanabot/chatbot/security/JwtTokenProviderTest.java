package com.tjanabot.chatbot.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SecurityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JwtTokenProvider
 * Tests JWT token generation, validation, and username extraction
 */
@DisplayName("JWT Token Provider Tests")
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private static final String TEST_SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha-256-algorithm-minimum-32-bytes";
    private static final String TEST_USERNAME = "test@example.com";

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(TEST_SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", 86400000L); // 24 hours
    }

    @Test
    @DisplayName("Should generate valid JWT token")
    void shouldGenerateValidToken() {
        // Act
        String token = jwtTokenProvider.generateToken(TEST_USERNAME);

        // Assert
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts: header.payload.signature
    }

    @Test
    @DisplayName("Should extract username from valid token")
    void shouldExtractUsername_whenTokenIsValid() {
        // Arrange
        String token = jwtTokenProvider.generateToken(TEST_USERNAME);

        // Act
        String extractedUsername = jwtTokenProvider.getUsernameFromToken(token);

        // Assert
        assertThat(extractedUsername).isEqualTo(TEST_USERNAME);
    }

    @Test
    @DisplayName("Should validate correct token successfully")
    void shouldValidateToken_whenTokenIsCorrect() {
        // Arrange
        String token = jwtTokenProvider.generateToken(TEST_USERNAME);

        // Act
        boolean isValid = jwtTokenProvider.validateToken(token);

        // Assert
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should reject empty token")
    void shouldRejectToken_whenTokenIsEmpty() {
        // Act
        boolean isValid = jwtTokenProvider.validateToken("");

        // Assert
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should reject null token")
    void shouldRejectToken_whenTokenIsNull() {
        // Act
        boolean isValid = jwtTokenProvider.validateToken(null);

        // Assert
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should reject malformed token")
    void shouldRejectToken_whenTokenIsMalformed() {
        // Arrange
        String malformedToken = "not.a.valid.jwt.token";

        // Act
        boolean isValid = jwtTokenProvider.validateToken(malformedToken);

        // Assert
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should reject token with invalid signature")
    void shouldRejectToken_whenSignatureIsInvalid() {
        // Arrange
        String token = jwtTokenProvider.generateToken(TEST_USERNAME);
        // Tamper with the token by changing the last character
        String tamperedToken = token.substring(0, token.length() - 1) + "X";

        // Act
        boolean isValid = jwtTokenProvider.validateToken(tamperedToken);

        // Assert
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should reject expired token")
    void shouldRejectToken_whenTokenIsExpired() {
        // Arrange: Create a provider with very short expiration (1ms)
        JwtTokenProvider shortExpirationProvider = new JwtTokenProvider(TEST_SECRET);
        ReflectionTestUtils.setField(shortExpirationProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(shortExpirationProvider, "jwtExpiration", 1L); // 1 millisecond

        String token = shortExpirationProvider.generateToken(TEST_USERNAME);

        // Wait for token to expire
        try {
            Thread.sleep(10); // Wait 10ms to ensure expiration
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        boolean isValid = shortExpirationProvider.validateToken(token);

        // Assert
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should generate different tokens for different users")
    void shouldGenerateDifferentTokens_forDifferentUsers() {
        // Arrange
        String user1 = "user1@example.com";
        String user2 = "user2@example.com";

        // Act
        String token1 = jwtTokenProvider.generateToken(user1);
        String token2 = jwtTokenProvider.generateToken(user2);

        // Assert
        assertThat(token1).isNotEqualTo(token2);
        assertThat(jwtTokenProvider.getUsernameFromToken(token1)).isEqualTo(user1);
        assertThat(jwtTokenProvider.getUsernameFromToken(token2)).isEqualTo(user2);
    }

    @Test
    @DisplayName("Should generate different tokens for same user at different times")
    void shouldGenerateDifferentTokens_forSameUserAtDifferentTimes() throws InterruptedException {
        // Arrange & Act
        String token1 = jwtTokenProvider.generateToken(TEST_USERNAME);
        Thread.sleep(1000); // 1 second delay to ensure different issuedAt timestamp (JWT uses seconds)
        String token2 = jwtTokenProvider.generateToken(TEST_USERNAME);

        // Assert
        assertThat(token1).isNotEqualTo(token2); // Different issuedAt times
        assertThat(jwtTokenProvider.getUsernameFromToken(token1)).isEqualTo(TEST_USERNAME);
        assertThat(jwtTokenProvider.getUsernameFromToken(token2)).isEqualTo(TEST_USERNAME);
    }

    @Test
    @DisplayName("Should throw exception when extracting username from invalid token")
    void shouldThrowException_whenExtractingUsernameFromInvalidToken() {
        // Arrange
        String invalidToken = "invalid.token.here";

        // Act & Assert
        assertThatThrownBy(() -> jwtTokenProvider.getUsernameFromToken(invalidToken))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should handle token with special characters in username")
    void shouldHandleToken_withSpecialCharactersInUsername() {
        // Arrange
        String specialUsername = "user+tag@example.com";

        // Act
        String token = jwtTokenProvider.generateToken(specialUsername);
        String extractedUsername = jwtTokenProvider.getUsernameFromToken(token);

        // Assert
        assertThat(extractedUsername).isEqualTo(specialUsername);
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("Should handle token with unicode characters in username")
    void shouldHandleToken_withUnicodeCharactersInUsername() {
        // Arrange
        String unicodeUsername = "用户@example.com";

        // Act
        String token = jwtTokenProvider.generateToken(unicodeUsername);
        String extractedUsername = jwtTokenProvider.getUsernameFromToken(token);

        // Assert
        assertThat(extractedUsername).isEqualTo(unicodeUsername);
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("Should reject token created with different secret")
    void shouldRejectToken_whenCreatedWithDifferentSecret() {
        // Arrange
        String differentSecret = "different-secret-key-that-is-also-long-enough-for-hmac-sha-256-algorithm";
        JwtTokenProvider differentProvider = new JwtTokenProvider(differentSecret);
        ReflectionTestUtils.setField(differentProvider, "jwtSecret", differentSecret);
        ReflectionTestUtils.setField(differentProvider, "jwtExpiration", 86400000L);

        String tokenFromDifferentProvider = differentProvider.generateToken(TEST_USERNAME);

        // Act
        boolean isValid = jwtTokenProvider.validateToken(tokenFromDifferentProvider);

        // Assert
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should handle very long username")
    void shouldHandleToken_withVeryLongUsername() {
        // Arrange
        String longUsername = "a".repeat(255) + "@example.com";

        // Act
        String token = jwtTokenProvider.generateToken(longUsername);
        String extractedUsername = jwtTokenProvider.getUsernameFromToken(token);

        // Assert
        assertThat(extractedUsername).isEqualTo(longUsername);
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }
}
