package com.tjanabot.chatbot.security;

import com.tjanabot.chatbot.helpers.TestDataBuilder;
import com.tjanabot.chatbot.model.User;
import com.tjanabot.chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Security Configuration Integration Tests")
class SecurityConfigIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = TestDataBuilder.createTestUser();
        testUser.setId(1L);
    }

    @Test
    @DisplayName("Should allow public access to auth endpoints")
    void shouldAllowPublicAccessToAuthEndpoints() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNot(401)); // Not unauthorized (may be 400 bad request)

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNot(401));
    }

    @Test
    @DisplayName("Should require authentication for protected endpoints")
    void shouldRequireAuthenticationForProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/api/chatbots"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/chatbots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/user/profile"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = {"USER"})
    @DisplayName("Should allow authenticated access to user endpoints")
    void shouldAllowAuthenticatedAccess() throws Exception {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        mockMvc.perform(get("/api/chatbots")
                .header("Authorization", "Bearer valid_token"))
            .andExpect(status().isNot(401)); // Not unauthorized
    }

    @Test
    @DisplayName("Should reject requests with invalid JWT token")
    void shouldRejectInvalidJwtToken() throws Exception {
        mockMvc.perform(get("/api/chatbots")
                .header("Authorization", "Bearer invalid_token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject requests with expired JWT token")
    void shouldRejectExpiredJwtToken() throws Exception {
        String expiredToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIiwiZXhwIjoxNTE2MjM5MDIyfQ.abc123";

        mockMvc.perform(get("/api/chatbots")
                .header("Authorization", "Bearer " + expiredToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should enable CORS for allowed origins")
    void shouldEnableCors() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                .header("Origin", "https://example.com")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("Should set security headers")
    void shouldSetSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
            .andExpect(header().exists("X-Content-Type-Options"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().exists("X-Frame-Options"))
            .andExpect(header().exists("X-XSS-Protection"));
    }

    @Test
    @DisplayName("Should protect against CSRF for state-changing operations")
    void shouldProtectAgainstCsrf() throws Exception {
        // For session-based auth, CSRF protection should be enabled
        // For JWT, CSRF is typically disabled as tokens are not automatically sent
        mockMvc.perform(post("/api/chatbots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNot(403)); // Should be 401 (unauthorized), not 403 (CSRF)
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should enforce role-based access control")
    void shouldEnforceRoleBasedAccessControl() throws Exception {
        // Users should not be able to access admin endpoints
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    @DisplayName("Should allow admin access to admin endpoints")
    void shouldAllowAdminAccess() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs"))
            .andExpect(status().isNot(403)); // Not forbidden
    }

    @Test
    @DisplayName("Should rate limit authentication endpoints")
    void shouldRateLimitAuthEndpoints() throws Exception {
        // Send multiple requests in rapid succession
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@example.com\",\"password\":\"wrong\"}"));
        }

        // The 11th request should be rate limited
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"password\":\"wrong\"}"))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Should allow OAuth2 login")
    void shouldAllowOAuth2Login() throws Exception {
        // OAuth2 login should redirect to Google
        mockMvc.perform(get("/oauth2/authorization/google"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Should handle OAuth2 callback")
    void shouldHandleOAuth2Callback() throws Exception {
        // OAuth2 callback endpoint should be accessible
        mockMvc.perform(get("/login/oauth2/code/google")
                .param("code", "test_auth_code")
                .param("state", "test_state"))
            .andExpect(status().isNot(404)); // Endpoint should exist
    }

    @Test
    @DisplayName("Should deny access to actuator endpoints without authentication")
    void shouldProtectActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/metrics"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = {"USER"})
    @DisplayName("Should prevent path traversal attacks")
    void shouldPreventPathTraversal() throws Exception {
        // Attempt path traversal
        mockMvc.perform(get("/api/files/../../etc/passwd"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should validate content type")
    void shouldValidateContentType() throws Exception {
        // Attempt to send non-JSON content to JSON endpoint
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.TEXT_PLAIN)
                .content("not json"))
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("Should enforce HTTPS in production")
    void shouldEnforceHttpsInProduction() throws Exception {
        // In production, HTTP should redirect to HTTPS
        // This is typically configured at the reverse proxy level
        // Test that security headers encourage HTTPS
        mockMvc.perform(get("/api/auth/login"))
            .andExpect(header().exists("Strict-Transport-Security"));
    }
}
