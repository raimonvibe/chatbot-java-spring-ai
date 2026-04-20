package com.prayer_chat.chatbot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.security.CustomOAuth2UserService;
import com.prayer_chat.chatbot.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security and functionality tests for hybrid OAuth2 callback endpoint
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthController OAuth2 Security Tests")
class AuthControllerOAuth2Test {

    @InjectMocks
    private AuthController authController;

    @Mock
    private CustomOAuth2UserService customOAuth2UserService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RestTemplate restTemplate;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private String validCode;
    private String validRedirectUri;
    private Map<String, Object> mockTokenResponse;
    private Map<String, Object> mockUserInfo;
    private User mockUser;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Inject mocks before building MockMvc so controller has RestTemplate and OAuth config
        ReflectionTestUtils.setField(authController, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(authController, "googleClientId", "test-client-id");
        ReflectionTestUtils.setField(authController, "googleClientSecret", "test-client-secret");
        ReflectionTestUtils.setField(authController, "allowedOrigins", "http://localhost:3000,https://prayer-chat.com,https://www.prayer-chat.com");
        // Standalone MockMvc does not load application-test.yml; match integration tests that expect JSON token.
        ReflectionTestUtils.setField(authController, "exposeJwtInOAuthResponse", true);

        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();

        // Valid test data
        validCode = "4/0AeanS1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        validRedirectUri = "http://localhost:3000/auth/callback";

        // Mock token response from Google
        mockTokenResponse = new HashMap<>();
        mockTokenResponse.put("access_token", "ya29.a0AfH6SMB1234567890");
        mockTokenResponse.put("token_type", "Bearer");
        mockTokenResponse.put("expires_in", 3600);

        // Mock user info from Google
        mockUserInfo = new HashMap<>();
        mockUserInfo.put("sub", "123456789");
        mockUserInfo.put("email", "test@example.com");
        mockUserInfo.put("name", "Test User");
        mockUserInfo.put("picture", "https://example.com/photo.jpg");

        // Mock user entity
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("test@example.com");
        mockUser.setUsername("test@example.com");
        mockUser.setGoogleId("123456789");
        mockUser.setAuthProvider(User.AuthProvider.GOOGLE);
        mockUser.getRoles().add("USER");
    }

    @Test
    @DisplayName("Should successfully exchange code for token and return JWT")
    void shouldSuccessfullyExchangeCodeForToken() throws Exception {
        // Arrange
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockTokenResponse));

        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockUserInfo));

        OAuth2User oauth2User = new DefaultOAuth2User(
                Collections.singletonList(() -> "USER"),
                mockUserInfo,
                "sub"
        );

        when(customOAuth2UserService.processOAuth2UserDirectly(
                any(OAuth2User.class), anyString(), anyString(), anyString()))
                .thenReturn(mockUser);

        when(jwtTokenProvider.generateToken(anyString(), anyLong()))
                .thenReturn("mock-jwt-token");

        Map<String, String> request = Map.of(
                "code", validCode,
                "redirect_uri", validRedirectUri
        );

        // Act & Assert
        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.token").value("mock-jwt-token"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.authProvider").value("GOOGLE"))
                .andExpect(jsonPath("$.user.name").value("test@example.com")); // Falls back to email if name is null
    }

    @Test
    @DisplayName("Should reject request with missing authorization code")
    void shouldRejectMissingCode() throws Exception {
        Map<String, String> request = Map.of(
                "redirect_uri", validRedirectUri
        );

        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Should reject request with empty authorization code")
    void shouldRejectEmptyCode() throws Exception {
        Map<String, String> request = Map.of(
                "code", "",
                "redirect_uri", validRedirectUri
        );

        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Should reject request with code that is too short")
    void shouldRejectCodeTooShort() throws Exception {
        Map<String, String> request = Map.of(
                "code", "short",
                "redirect_uri", validRedirectUri
        );

        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Should reject request with code that is too long")
    void shouldRejectCodeTooLong() throws Exception {
        String longCode = "a".repeat(501); // Exceeds MAX_CODE_LENGTH
        Map<String, String> request = Map.of(
                "code", longCode,
                "redirect_uri", validRedirectUri
        );

        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Should reject request with invalid code format (contains special characters)")
    void shouldRejectInvalidCodeFormat() throws Exception {
        Map<String, String> request = Map.of(
                "code", "invalid<code>with<script>tags",
                "redirect_uri", validRedirectUri
        );

        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Should reject request with missing redirect URI")
    void shouldRejectMissingRedirectUri() throws Exception {
        Map<String, String> request = Map.of(
                "code", validCode
        );

        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Should reject request with invalid redirect URI (not in allowed origins)")
    void shouldRejectInvalidRedirectUri() throws Exception {
        Map<String, String> request = Map.of(
                "code", validCode,
                "redirect_uri", "https://evil.com/auth/callback"
        );

        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("SECURITY: Reject redirect URI subdomain bypass (www.prayer-chat.com.evil.com)")
    void security_rejectRedirectUriSubdomainBypass() throws Exception {
        Map<String, String> request = Map.of(
                "code", validCode,
                "redirect_uri", "https://www.prayer-chat.com.evil.com/auth/callback"
        );

        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Should reject request with HTTP redirect URI in production (non-localhost)")
    void shouldRejectHttpRedirectUriInProduction() throws Exception {
        Map<String, String> request = Map.of(
                "code", validCode,
                "redirect_uri", "http://example.com/auth/callback"
        );

        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Should allow localhost redirect URI for development")
    void shouldAllowLocalhostRedirectUri() throws Exception {
        // Arrange
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockTokenResponse));

        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockUserInfo));

        OAuth2User oauth2User = new DefaultOAuth2User(
                Collections.singletonList(() -> "USER"),
                mockUserInfo,
                "sub"
        );

        when(customOAuth2UserService.processOAuth2UserDirectly(
                any(OAuth2User.class), anyString(), anyString(), anyString()))
                .thenReturn(mockUser);

        when(jwtTokenProvider.generateToken(anyString(), anyLong()))
                .thenReturn("mock-jwt-token");

        Map<String, String> request = Map.of(
                "code", validCode,
                "redirect_uri", "http://localhost:3000/auth/callback"
        );

        // Act & Assert
        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should handle Google token exchange failure gracefully")
    void shouldHandleTokenExchangeFailure() throws Exception {
        // Arrange - Simulate invalid_grant error (expired/used code)
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "invalid_grant");
        errorResponse.put("error_description", "Bad Request");
        
        ResponseEntity<Map<String, Object>> errorEntity = 
                ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
        
        @SuppressWarnings("unchecked")
        ResponseEntity<Map> rawResponse = (ResponseEntity<Map>) (ResponseEntity<?>) errorEntity;
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(rawResponse);

        Map<String, String> request = Map.of(
                "code", validCode,
                "redirect_uri", validRedirectUri
        );

        // Act & Assert - Should return 400 with specific error message
        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Authorization code expired"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Should handle Google user info fetch failure gracefully")
    void shouldHandleUserInfoFetchFailure() throws Exception {
        // Arrange
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockTokenResponse));

        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                .thenThrow(new RestClientException("Invalid token"));

        Map<String, String> request = Map.of(
                "code", validCode,
                "redirect_uri", validRedirectUri
        );

        // Act & Assert
        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Should not leak sensitive error details in response")
    void shouldNotLeakSensitiveErrorDetails() throws Exception {
        // Arrange - Simulate a generic error (not invalid_grant)
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "invalid_client");
        errorResponse.put("error_description", "Bad Request");
        
        ResponseEntity<Map<String, Object>> errorEntity = 
                ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
        
        @SuppressWarnings("unchecked")
        ResponseEntity<Map> rawResponse = (ResponseEntity<Map>) (ResponseEntity<?>) errorEntity;
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(rawResponse);

        Map<String, String> request = Map.of(
                "code", validCode,
                "redirect_uri", validRedirectUri
        );

        // Act & Assert
        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value("Authentication failed"))
                .andExpect(jsonPath("$.message").doesNotExist()); // Should not expose detailed error
    }

    @Test
    @DisplayName("Should handle null request body gracefully")
    void shouldHandleNullRequestBody() throws Exception {
        // With standalone setup, "null" body can trigger HttpMessageNotReadableException before controller;
        // we only assert 400 so the client gets a clear bad-request signal.
        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should trim whitespace from code and redirect URI")
    void shouldTrimWhitespaceFromInput() throws Exception {
        // Arrange
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockTokenResponse));

        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockUserInfo));

        OAuth2User oauth2User = new DefaultOAuth2User(
                Collections.singletonList(() -> "USER"),
                mockUserInfo,
                "sub"
        );

        when(customOAuth2UserService.processOAuth2UserDirectly(
                any(OAuth2User.class), anyString(), anyString(), anyString()))
                .thenReturn(mockUser);

        when(jwtTokenProvider.generateToken(anyString(), anyLong()))
                .thenReturn("mock-jwt-token");

        Map<String, String> request = Map.of(
                "code", "  " + validCode + "  ",
                "redirect_uri", "  " + validRedirectUri + "  "
        );

        // Act & Assert - should work with trimmed values
        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should validate redirect URI contains /auth/callback path")
    void shouldValidateCallbackPath() throws Exception {
        Map<String, String> request = Map.of(
                "code", validCode,
                "redirect_uri", "http://localhost:3000/other/path"
        );

        mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}

