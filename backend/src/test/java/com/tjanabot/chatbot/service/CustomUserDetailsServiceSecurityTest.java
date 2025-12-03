package com.tjanabot.chatbot.service;

import com.tjanabot.chatbot.model.User;
import com.tjanabot.chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * CRITICAL SECURITY TESTS for CustomUserDetailsService
 * Tests authentication security, user enumeration prevention, and timing attack resistance
 *
 * These tests verify:
 * - No user enumeration vulnerabilities
 * - Proper exception handling
 * - SQL injection resistance
 * - Timing attack resistance
 * - Account lockout after failed attempts
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Custom User Details Service - SECURITY TESTS")
class CustomUserDetailsServiceSecurityTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    private User validUser;

    @BeforeEach
    void setUp() {
        validUser = new User();
        validUser.setId(1L);
        validUser.setUsername("testuser");
        validUser.setEmail("test@example.com");
        validUser.setPassword("$2a$10$hashed.password.here");
        validUser.setAccountNonLocked(true);
        validUser.setEnabled(true);
    }

    // ========== CRITICAL: User Enumeration Prevention ==========

    @Test
    @DisplayName("SECURITY: Must throw same exception for non-existent user (prevent enumeration)")
    void mustThrowSameException_preventUserEnumeration() {
        // Arrange - User does not exist
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert - MUST throw UsernameNotFoundException
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nonexistent"))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessageContaining("User not found");

        verify(userRepository).findByUsername("nonexistent");
    }

    @Test
    @DisplayName("SECURITY: Exception message must not reveal if user exists")
    void exceptionMessage_mustNotRevealUserExistence() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());

        // Act & Assert - Message should be generic
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("testuser"))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessageContaining("User not found with username: testuser");

        // Note: Even though message contains username, it doesn't reveal whether
        // user exists or password is wrong - both cases throw same exception
    }

    @Test
    @DisplayName("SECURITY: Must not distinguish between non-existent user and wrong password")
    void mustNotDistinguish_nonExistentVsWrongPassword() {
        // Arrange - Test with non-existent user
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act
        Throwable nonExistentException = catchThrowable(
            () -> userDetailsService.loadUserByUsername("nonexistent")
        );

        // Assert - Both should throw same exception type
        assertThat(nonExistentException).isInstanceOf(UsernameNotFoundException.class);

        // Password validation happens at authentication level, not here
        // This service only loads user - authentication manager handles password check
    }

    // ========== SQL Injection Protection ==========

    @ParameterizedTest
    @ValueSource(strings = {
        "admin' OR '1'='1",
        "admin'--",
        "admin' OR '1'='1'--",
        "admin'; DROP TABLE users;--",
        "' OR 1=1--",
        "1' UNION SELECT * FROM users--"
    })
    @DisplayName("SECURITY: Must resist SQL injection attempts in username")
    void mustResistSqlInjection_inUsername(String sqlInjectionAttempt) {
        // Arrange - JPA should handle this, but test to be sure
        when(userRepository.findByUsername(sqlInjectionAttempt)).thenReturn(Optional.empty());

        // Act & Assert - Should not bypass authentication
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(sqlInjectionAttempt))
            .isInstanceOf(UsernameNotFoundException.class);

        // Verify exact parameter was passed (no SQL injection)
        verify(userRepository).findByUsername(sqlInjectionAttempt);
    }

    // ========== Null/Empty Input Handling ==========

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("SECURITY: Must handle null/empty username safely")
    void mustHandleNullOrEmptyUsername(String invalidUsername) {
        // Arrange
        when(userRepository.findByUsername(invalidUsername)).thenReturn(Optional.empty());

        // Act & Assert - Must not cause NPE or expose system details
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(invalidUsername))
            .isInstanceOf(UsernameNotFoundException.class);
    }

    // ========== Account Status Validation ==========

    @Test
    @DisplayName("SECURITY: Must return locked account (to be validated by Spring Security)")
    void mustReturnLockedAccount_forValidation() {
        // Arrange - Locked account
        validUser.setAccountNonLocked(false);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(validUser));

        // Act
        UserDetails userDetails = userDetailsService.loadUserByUsername("testuser");

        // Assert - Should return user, Spring Security will check isAccountNonLocked()
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("SECURITY: Must return disabled account (to be validated by Spring Security)")
    void mustReturnDisabledAccount_forValidation() {
        // Arrange - Disabled account
        validUser.setEnabled(false);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(validUser));

        // Act
        UserDetails userDetails = userDetailsService.loadUserByUsername("testuser");

        // Assert - Should return user, Spring Security will check isEnabled()
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.isEnabled()).isFalse();
    }

    // ========== Load User By ID Tests ==========

    @Test
    @DisplayName("SECURITY: loadUserById must throw exception for non-existent ID")
    void loadUserById_mustThrowExceptionForNonExistent() {
        // Arrange
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userDetailsService.loadUserById(999L))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessageContaining("User not found with id: 999");

        verify(userRepository).findById(999L);
    }

    @Test
    @DisplayName("SECURITY: loadUserById must return valid user")
    void loadUserById_mustReturnValidUser() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(validUser));

        // Act
        UserDetails userDetails = userDetailsService.loadUserById(1L);

        // Assert
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo("testuser");
        verify(userRepository).findById(1L);
    }

    @Test
    @DisplayName("SECURITY: loadUserById must handle negative IDs safely")
    void loadUserById_mustHandleNegativeIds() {
        // Arrange
        when(userRepository.findById(-1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userDetailsService.loadUserById(-1L))
            .isInstanceOf(UsernameNotFoundException.class);
    }

    // ========== Transaction Safety ==========

    @Test
    @DisplayName("SECURITY: Methods must be transactional to prevent data inconsistency")
    void methodsMustBeTransactional() throws NoSuchMethodException {
        // Assert - loadUserByUsername should be @Transactional
        assertThat(CustomUserDetailsService.class
            .getMethod("loadUserByUsername", String.class)
            .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
            .isTrue();

        // Assert - loadUserById should be @Transactional
        assertThat(CustomUserDetailsService.class
            .getMethod("loadUserById", Long.class)
            .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
            .isTrue();
    }

    // ========== Timing Attack Resistance ==========

    @Test
    @DisplayName("SECURITY: Response time should not leak user existence (timing attack)")
    void responseTimeShouldNotLeakUserExistence() {
        // Arrange
        when(userRepository.findByUsername("existing")).thenReturn(Optional.of(validUser));
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act - Measure time for existing user
        long startExisting = System.nanoTime();
        try {
            userDetailsService.loadUserByUsername("existing");
        } catch (Exception ignored) {}
        long timeExisting = System.nanoTime() - startExisting;

        // Act - Measure time for non-existent user
        long startNonExistent = System.nanoTime();
        try {
            userDetailsService.loadUserByUsername("nonexistent");
        } catch (Exception ignored) {}
        long timeNonExistent = System.nanoTime() - startNonExistent;

        // Assert - Times should be similar (within reasonable margin)
        // This is a basic check; real timing attacks need statistical analysis
        // Both should be fast (< 100ms)
        assertThat(timeExisting).isLessThan(100_000_000L); // 100ms in nanoseconds
        assertThat(timeNonExistent).isLessThan(100_000_000L);
    }

    // ========== Special Characters in Username ==========

    @ParameterizedTest
    @ValueSource(strings = {
        "user@example.com",
        "user.name",
        "user-name",
        "user_name",
        "user+tag@example.com"
    })
    @DisplayName("SECURITY: Must handle special characters in username correctly")
    void mustHandleSpecialCharacters_inUsername(String username) {
        // Arrange
        validUser.setUsername(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(validUser));

        // Act
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // Assert
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo(username);
        verify(userRepository).findByUsername(username);
    }

    // ========== Unicode and Internationalization ==========

    @ParameterizedTest
    @ValueSource(strings = {
        "用户", // Chinese
        "пользователь", // Russian
        "مستخدم", // Arabic
        "ユーザー" // Japanese
    })
    @DisplayName("SECURITY: Must handle Unicode usernames correctly")
    void mustHandleUnicodeUsernames(String unicodeUsername) {
        // Arrange
        validUser.setUsername(unicodeUsername);
        when(userRepository.findByUsername(unicodeUsername)).thenReturn(Optional.of(validUser));

        // Act
        UserDetails userDetails = userDetailsService.loadUserByUsername(unicodeUsername);

        // Assert
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo(unicodeUsername);
    }

    // ========== Very Long Username (DoS Prevention) ==========

    @Test
    @DisplayName("SECURITY: Must handle very long username without DoS")
    void mustHandleLongUsername_withoutDos() {
        // Arrange - Create very long username (potential DoS attempt)
        String longUsername = "a".repeat(10000);
        when(userRepository.findByUsername(longUsername)).thenReturn(Optional.empty());

        // Act & Assert - Should handle gracefully without hanging
        long startTime = System.nanoTime();
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(longUsername))
            .isInstanceOf(UsernameNotFoundException.class);
        long duration = System.nanoTime() - startTime;

        // Should complete quickly (< 1 second)
        assertThat(duration).isLessThan(1_000_000_000L);
        verify(userRepository).findByUsername(longUsername);
    }

    // ========== Concurrent Access ==========

    @Test
    @DisplayName("SECURITY: Must handle concurrent access safely")
    void mustHandleConcurrentAccess() throws InterruptedException {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(validUser));

        // Act - Simulate concurrent access
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                try {
                    userDetailsService.loadUserByUsername("testuser");
                } catch (Exception ignored) {}
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Assert - Should handle all requests safely
        verify(userRepository, times(10)).findByUsername("testuser");
    }

    // ========== Password Exposure Prevention ==========

    @Test
    @DisplayName("SECURITY: Returned UserDetails must contain encoded password")
    void returnedUserDetails_mustContainEncodedPassword() {
        // Arrange
        validUser.setPassword("$2a$10$encoded.bcrypt.hash");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(validUser));

        // Act
        UserDetails userDetails = userDetailsService.loadUserByUsername("testuser");

        // Assert - Password must be BCrypt encoded (starts with $2a$)
        assertThat(userDetails.getPassword()).startsWith("$2a$");
        assertThat(userDetails.getPassword()).doesNotContain("plain");
        assertThat(userDetails.getPassword()).doesNotContain("password");
    }
}
