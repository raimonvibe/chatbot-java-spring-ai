package com.prayer_chat.chatbot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.UserRepository;
import com.prayer_chat.chatbot.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import com.prayer_chat.chatbot.config.MockAiConfiguration;
import com.prayer_chat.chatbot.config.TestSecurityConfig;
import com.prayer_chat.chatbot.config.TestJacksonConfiguration;
import com.prayer_chat.chatbot.controller.AuthController;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for hybrid OAuth2 callback endpoint
 * Tests the full flow with mocked external Google API calls
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MockAiConfiguration.class, TestSecurityConfig.class, TestJacksonConfiguration.class})
@TestPropertySource(properties = {
        "GOOGLE_CLIENT_ID=test-client-id.apps.googleusercontent.com",
        "GOOGLE_CLIENT_SECRET=test-client-secret",
        "JWT_SECRET=test-jwt-secret-key-minimum-32-characters-long-for-security",
        "cors.allowed-origins=http://localhost:3000,https://prayer-chat.com,https://www.prayer-chat.com"
})
@DisplayName("AuthController OAuth2 Integration Tests")
class AuthControllerOAuth2IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthController authController;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    private RestTemplate mockRestTemplate;

    private String validCode;
    private String validRedirectUri;
    private Map<String, Object> mockTokenResponse;
    private Map<String, Object> mockUserInfo;

    @BeforeEach
    void setUp() {
        // Clean up test data
        userRepository.deleteAll();

        // Mock RestTemplate and inject it into AuthController using reflection
        mockRestTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(authController, "restTemplate", mockRestTemplate);

        // Valid test data
        validCode = "4/0AeanS1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        validRedirectUri = "http://localhost:3000/auth/callback";

        // Mock token response from Google
        mockTokenResponse = new HashMap<>();
        mockTokenResponse.put("access_token", "ya29.a0AfH6SMB1234567890");
        mockTokenResponse.put("token_type", "Bearer");
        mockTokenResponse.put("expires_in", 3600);
        mockTokenResponse.put("refresh_token", "1//refresh_token_here");

        // Mock user info from Google
        mockUserInfo = new HashMap<>();
        mockUserInfo.put("sub", "123456789");
        mockUserInfo.put("email", "test@example.com");
        mockUserInfo.put("name", "Test User");
        mockUserInfo.put("picture", "https://example.com/photo.jpg");
        mockUserInfo.put("email_verified", true);
    }

    @Test
    @DisplayName("Should create new user on first OAuth login")
    void shouldCreateNewUserOnFirstLogin() throws Exception {
        // Arrange
        when(mockRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockTokenResponse));

        when(mockRestTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockUserInfo));

        Map<String, String> request = Map.of(
                "code", validCode,
                "redirect_uri", validRedirectUri
        );

        // Act
        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.user.email").value("test@example.com"));

        // Assert - user should be created in database
        Optional<User> createdUser = userRepository.findByEmail("test@example.com");
        assert createdUser.isPresent();
        assert createdUser.get().getGoogleId().equals("123456789");
        assert createdUser.get().getAuthProvider() == User.AuthProvider.GOOGLE;
    }

    @Test
    @DisplayName("Should update existing user on subsequent OAuth login")
    void shouldUpdateExistingUserOnSubsequentLogin() throws Exception {
        // Arrange - create existing user
        User existingUser = new User();
        existingUser.setEmail("test@example.com");
        existingUser.setUsername("test@example.com");
        existingUser.setGoogleId("123456789");
        existingUser.setAuthProvider(User.AuthProvider.GOOGLE);
        existingUser.setEnabled(true);
        existingUser.getRoles().add("USER");
        userRepository.save(existingUser);

        when(mockRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockTokenResponse));

        when(mockRestTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockUserInfo));

        Map<String, String> request = Map.of(
                "code", validCode,
                "redirect_uri", validRedirectUri
        );

        // Act
        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());

        // Assert - user should be updated (lastLogin should be set)
        Optional<User> updatedUser = userRepository.findByEmail("test@example.com");
        assert updatedUser.isPresent();
        assert updatedUser.get().getLastLogin() != null;
    }

    @Test
    @DisplayName("Should link Google account to existing user with matching email")
    void shouldLinkGoogleAccountToExistingUser() throws Exception {
        // Arrange - create user without Google ID
        User existingUser = new User();
        existingUser.setEmail("test@example.com");
        existingUser.setUsername("test@example.com");
        existingUser.setAuthProvider(User.AuthProvider.GOOGLE);
        existingUser.setEnabled(true);
        existingUser.getRoles().add("USER");
        userRepository.save(existingUser);

        when(mockRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockTokenResponse));

        when(mockRestTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockUserInfo));

        Map<String, String> request = Map.of(
                "code", validCode,
                "redirect_uri", validRedirectUri
        );

        // Act
        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Assert - Google ID should be linked
        Optional<User> linkedUser = userRepository.findByEmail("test@example.com");
        assert linkedUser.isPresent();
        assert linkedUser.get().getGoogleId().equals("123456789");
    }

    @Test
    @DisplayName("Should generate valid JWT token")
    void shouldGenerateValidJwtToken() throws Exception {
        // Arrange
        when(mockRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockTokenResponse));

        when(mockRestTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockUserInfo));

        Map<String, String> request = Map.of(
                "code", validCode,
                "redirect_uri", validRedirectUri
        );

        // Act
        String response = mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Assert - extract and validate JWT token
        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        String token = (String) responseMap.get("token");
        assert token != null;
        assert !token.isEmpty();
        assert jwtTokenProvider.validateToken(token);
    }

    @Test
    @DisplayName("Should handle production redirect URI with HTTPS")
    void shouldHandleProductionRedirectUri() throws Exception {
        // Arrange
        when(mockRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockTokenResponse));

        when(mockRestTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockUserInfo));

        Map<String, String> request = Map.of(
                "code", validCode,
                "redirect_uri", "https://prayer-chat.com/auth/callback"
        );

        // Act & Assert
        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    @DisplayName("Should handle www subdomain redirect URI")
    void shouldHandleWwwSubdomainRedirectUri() throws Exception {
        // Arrange
        when(mockRestTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockTokenResponse));

        when(mockRestTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockUserInfo));

        Map<String, String> request = Map.of(
                "code", validCode,
                "redirect_uri", "https://www.prayer-chat.com/auth/callback"
        );

        // Act & Assert
        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }
}

