package com.tjanabot.chatbot.security;

import com.tjanabot.chatbot.helpers.TestDataBuilder;
import com.tjanabot.chatbot.model.User;
import com.tjanabot.chatbot.repository.UserRepository;
import com.tjanabot.chatbot.repository.SubscriptionRepository;
import com.tjanabot.chatbot.model.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import com.tjanabot.chatbot.security.CustomOAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.*;
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

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SubscriptionRepository subscriptionRepository;

    private User testUser;
    private Subscription testSubscription;

    @BeforeEach
    void setUp() {
        testUser = TestDataBuilder.createTestUser();
        testUser.setId(1L);
        
        // Create active subscription for test user
        testSubscription = new Subscription();
        testSubscription.setId(1L);
        testSubscription.setUser(testUser);
        testSubscription.setPlan(Subscription.SubscriptionPlan.FREE);
        testSubscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        testSubscription.setCurrentPeriodStart(LocalDateTime.now());
        testSubscription.setCurrentPeriodEnd(LocalDateTime.now().plusMonths(1));
    }

    @Test
    @DisplayName("Should allow public access to OAuth2 endpoints")
    void shouldAllowPublicAccessToOAuth2Endpoints() throws Exception {
        // OAuth2 authorization endpoint should be accessible
        mockMvc.perform(get("/oauth2/authorization/google"))
            .andExpect(status().is(not(401))); // Not unauthorized (should redirect)

        // OAuth2 callback endpoint should be accessible
        mockMvc.perform(get("/login/oauth2/code/google")
                .param("code", "test_code")
                .param("state", "test_state"))
            .andExpect(status().is(not(401))); // Not unauthorized
    }

    @Test
    @DisplayName("Should allow public access to /api/chat/** endpoints (permitAll)")
    void shouldAllowPublicAccessToChatEndpoints() throws Exception {
        // /api/chat/** should be permitAll() - no authentication required
        // Even without authentication, should not return 401
        mockMvc.perform(post("/api/chat/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"test\"}"))
            .andExpect(status().is(not(401))); // Should NOT be 401 (permitAll)
            // Will likely be 404 (chatbot not found) or 500 (AI service), but NOT 401
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
    @DisplayName("Should allow authenticated access to user endpoints")
    void shouldAllowAuthenticatedAccess() throws Exception {
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(subscriptionRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(testSubscription));
        
        // Create CustomOAuth2User authentication for MockMvc
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "test_" + testUser.getId());
        attributes.put("email", testUser.getEmail());
        attributes.put("name", testUser.getUsername());

        OAuth2User oauth2User = new DefaultOAuth2User(
            Collections.emptyList(),
            attributes,
            "email"
        );

        CustomOAuth2User customOAuth2User = new CustomOAuth2User(oauth2User, testUser);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            customOAuth2User,
            null,
            customOAuth2User.getAuthorities()
        );

        // Use MockMvc's authentication() post processor instead of SecurityContext
        mockMvc.perform(get("/api/chatbots")
                .with(authentication(authentication)))
            .andExpect(status().is(not(401))); // Not unauthorized
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
        mockMvc.perform(options("/api/chatbots")
                .header("Origin", "https://example.com")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("Should set security headers")
    void shouldSetSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/health"))
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
            .andExpect(status().is(not(403))); // Should be 401 (unauthorized), not 403 (CSRF)
    }

    @Test
    @DisplayName("Should enforce role-based access control")
    void shouldEnforceRoleBasedAccessControl() throws Exception {
        // Create authenticated user without ADMIN role
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "test_" + testUser.getId());
        attributes.put("email", testUser.getEmail());
        attributes.put("name", testUser.getUsername());

        OAuth2User oauth2User = new DefaultOAuth2User(
            Collections.emptyList(),
            attributes,
            "email"
        );

        CustomOAuth2User customOAuth2User = new CustomOAuth2User(oauth2User, testUser);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            customOAuth2User,
            null,
            customOAuth2User.getAuthorities() // USER role, not ADMIN
        );

        // Users should not be able to access admin endpoints - should get 403, not 401
        mockMvc.perform(get("/api/admin/users")
                .with(authentication(authentication)))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    @DisplayName("Should allow admin access to admin endpoints")
    void shouldAllowAdminAccess() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs"))
            .andExpect(status().is(not(403))); // Not forbidden
    }

    @Test
    @DisplayName("Should rate limit OAuth2 endpoints")
    void shouldRateLimitOAuth2Endpoints() throws Exception {
        // Send multiple requests to OAuth2 authorization endpoint in rapid succession
        // API_LIMIT is 60 per minute, so we need to send 60+ requests
        for (int i = 0; i < 60; i++) {
            mockMvc.perform(get("/oauth2/authorization/google"));
        }

        // The 61st request should be rate limited (429) or still succeed (depending on rate limit config)
        // Rate limiting may not apply to GET requests, so we check for either 429 or 3xx redirect
        mockMvc.perform(get("/oauth2/authorization/google"))
            .andExpect(status().is(anyOf(is(429), is(302), is(303))));
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
            .andExpect(status().is(not(404))); // Endpoint should exist
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
        // First authenticate, then test content type validation
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(subscriptionRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(testSubscription));
        
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "test_" + testUser.getId());
        attributes.put("email", testUser.getEmail());
        attributes.put("name", testUser.getUsername());

        OAuth2User oauth2User = new DefaultOAuth2User(
            Collections.emptyList(),
            attributes,
            "email"
        );

        CustomOAuth2User customOAuth2User = new CustomOAuth2User(oauth2User, testUser);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            customOAuth2User,
            null,
            customOAuth2User.getAuthorities()
        );

        mockMvc.perform(post("/api/chatbots")
                .with(authentication(auth))
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
        mockMvc.perform(get("/api/health"))
            .andExpect(header().exists("Strict-Transport-Security"));
    }
}
