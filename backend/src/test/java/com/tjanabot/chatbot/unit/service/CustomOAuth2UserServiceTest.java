package com.tjanabot.chatbot.unit.service;

import com.tjanabot.chatbot.model.User;
import com.tjanabot.chatbot.repository.UserRepository;
import com.tjanabot.chatbot.security.CustomOAuth2UserService;
import com.tjanabot.chatbot.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomOAuth2UserService Unit Tests")
class CustomOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private DefaultOAuth2UserService delegate;

    @InjectMocks
    private CustomOAuth2UserService customOAuth2UserService;

    @Mock
    private OAuth2UserRequest userRequest;

    @Mock
    private OAuth2User oauth2User;

    private Map<String, Object> userAttributes;

    @BeforeEach
    void setUp() {
        userAttributes = new HashMap<>();
        userAttributes.put("sub", "google_12345");
        userAttributes.put("email", "newuser@gmail.com");
        userAttributes.put("name", "New User");
        userAttributes.put("picture", "https://example.com/photo.jpg");

        when(oauth2User.getAttributes()).thenReturn(userAttributes);
        when(delegate.loadUser(userRequest)).thenReturn(oauth2User);
    }

    @Test
    @DisplayName("Should create new user for first-time Google login")
    void shouldCreateNewUserForFirstTimeGoogleLogin() {
        // Arrange
        when(userRepository.findByGoogleId("google_12345")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("newuser@gmail.com")).thenReturn(Optional.empty());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        // Act
        OAuth2User result = customOAuth2UserService.loadUser(userRequest);

        // Assert
        verify(userRepository, times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo("newuser@gmail.com");
        assertThat(savedUser.getGoogleId()).isEqualTo("google_12345");
        assertThat(savedUser.getUsername()).isEqualTo("New User");
        assertThat(savedUser.getAuthProvider()).isEqualTo(User.AuthProvider.GOOGLE);
        assertThat(savedUser.getPictureUrl()).isEqualTo("https://example.com/photo.jpg");

        // Verify audit log was created
        verify(auditService, times(1)).log(any(), any(), anyString(), any(User.class), isNull(), isNull());
    }

    @Test
    @DisplayName("Should update existing user on subsequent Google login")
    void shouldUpdateExistingUserOnSubsequentLogin() {
        // Arrange
        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setEmail("newuser@gmail.com");
        existingUser.setGoogleId("google_12345");
        existingUser.setUsername("Old Name");
        existingUser.setAuthProvider(User.AuthProvider.GOOGLE);

        when(userRepository.findByGoogleId("google_12345")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        // Act
        OAuth2User result = customOAuth2UserService.loadUser(userRequest);

        // Assert
        verify(userRepository, times(1)).save(existingUser);
        assertThat(existingUser.getUsername()).isEqualTo("New User");
        assertThat(existingUser.getPictureUrl()).isEqualTo("https://example.com/photo.jpg");

        // Verify audit log for successful login
        verify(auditService, times(1)).log(any(), any(), anyString(), eq(existingUser), isNull(), isNull());
    }

    @Test
    @DisplayName("Should handle user with existing email but no Google ID")
    void shouldHandleExistingEmailWithoutGoogleId() {
        // Arrange
        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setEmail("newuser@gmail.com");
        existingUser.setUsername("Existing User");
        existingUser.setAuthProvider(User.AuthProvider.LOCAL);
        existingUser.setGoogleId(null);

        when(userRepository.findByGoogleId("google_12345")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("newuser@gmail.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        // Act
        OAuth2User result = customOAuth2UserService.loadUser(userRequest);

        // Assert
        verify(userRepository, times(1)).save(existingUser);
        assertThat(existingUser.getGoogleId()).isEqualTo("google_12345");
        assertThat(existingUser.getAuthProvider()).isEqualTo(User.AuthProvider.GOOGLE);

        verify(auditService, times(1)).log(any(), any(), anyString(), eq(existingUser), isNull(), isNull());
    }

    @Test
    @DisplayName("Should handle missing optional user attributes")
    void shouldHandleMissingOptionalAttributes() {
        // Arrange
        Map<String, Object> minimalAttributes = new HashMap<>();
        minimalAttributes.put("sub", "google_67890");
        minimalAttributes.put("email", "minimal@gmail.com");
        // No 'name' or 'picture'

        when(oauth2User.getAttributes()).thenReturn(minimalAttributes);
        when(userRepository.findByGoogleId("google_67890")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("minimal@gmail.com")).thenReturn(Optional.empty());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(2L);
            return user;
        });

        // Act
        OAuth2User result = customOAuth2UserService.loadUser(userRequest);

        // Assert
        verify(userRepository, times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo("minimal@gmail.com");
        assertThat(savedUser.getGoogleId()).isEqualTo("google_67890");
        assertThat(savedUser.getUsername()).isEqualTo("minimal@gmail.com"); // Fallback to email
        assertThat(savedUser.getPictureUrl()).isNull();
    }

    @Test
    @DisplayName("Should return OAuth2User with correct attributes")
    void shouldReturnOAuth2UserWithCorrectAttributes() {
        // Arrange
        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setEmail("test@gmail.com");
        existingUser.setGoogleId("google_12345");

        when(userRepository.findByGoogleId("google_12345")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        // Act
        OAuth2User result = customOAuth2UserService.loadUser(userRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getAttributes()).isEqualTo(userAttributes);
        assertThat(result.getName()).isEqualTo("google_12345"); // Default name attribute
    }

    @Test
    @DisplayName("Should handle delegate service failure")
    void shouldHandleDelegateServiceFailure() {
        // Arrange
        when(delegate.loadUser(userRequest))
            .thenThrow(new RuntimeException("OAuth provider error"));

        // Act & Assert
        assertThatThrownBy(() -> customOAuth2UserService.loadUser(userRequest))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("OAuth provider error");

        // Verify no user was saved
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should set last login timestamp")
    void shouldSetLastLoginTimestamp() {
        // Arrange
        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setEmail("test@gmail.com");
        existingUser.setGoogleId("google_12345");
        existingUser.setLastLogin(null);

        when(userRepository.findByGoogleId("google_12345")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        // Act
        customOAuth2UserService.loadUser(userRequest);

        // Assert
        assertThat(existingUser.getLastLogin()).isNotNull();
        verify(userRepository, times(1)).save(existingUser);
    }
}
