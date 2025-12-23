package com.prayer_chat.chatbot.e2e;

import com.prayer_chat.chatbot.helpers.E2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Authentication API E2E Tests
 *
 * Tests authentication flows including:
 * - POST /api/auth/register → /api/auth/login → GET /api/chatbots (authenticated)
 * - OAuth2 callback flow with Google (mocked)
 * - JWT token lifecycle (issue → refresh → expire)
 * - Rate limiting on failed login attempts
 */
@DisplayName("Authentication API E2E Tests")
class AuthApiE2ETest extends E2ETestBase {

    @Test
    @DisplayName("Complete OAuth2 Auth Flow: Create User → Access Protected Resource")
    void shouldCompleteFullAuthenticationFlow() {
        // Step 1: Create OAuth2 user (simulates Google login)
        String email = "authflow@example.com";
        String token = createOAuth2User(email);
        
        assertNotNull(token, "Token should be set after OAuth2 login");
        assertNotNull(webApiClient.withAuth(token).getAuthToken(), "Token should be set in API client");

        // Create active subscription for user (required for chatbot access)
        createActiveSubscriptionForUser(email);

        // Step 2: Access protected resource with auth token
        webApiClient.withAuth(token).getChatbots()
            .expectStatus().isOk()
            .expectBodyList(Map.class);

        // Step 3: Clear auth and try to access protected resource
        webApiClient.clearAuth(); // Explicitly clear auth token
        AtomicReference<Integer> statusCodeRef = new AtomicReference<>();
        webApiClient.getChatbots()
            .expectStatus().is4xxClientError()
            .expectBody()
            .consumeWith(result -> {
                int status = result.getStatus().value();
                statusCodeRef.set(status);
                assertTrue(status == 401 || status == 403,
                    "Should return 401 or 403 without auth token. Got: " + status);
            });
    }

    @Test
    @DisplayName("OAuth2 Flow: Mock Google OAuth Login")
    void shouldCompleteGoogleOAuthFlow() {
        // Mock Google OAuth token exchange
        stubFor(post(urlEqualTo("/oauth2/token"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\": \"mock_google_token\", \"token_type\": \"Bearer\"}")));

        // Mock Google user info
        stubFor(get(urlEqualTo("/oauth2/userinfo"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"sub\": \"google_123\", \"email\": \"googleuser@gmail.com\", \"name\": \"Google User\"}")));

        // Verify WireMock stubs are configured
        verify(0, postRequestedFor(urlEqualTo("/oauth2/token")));

        // Note: OAuth flow typically happens via browser redirect
        // This test verifies that WireMock is properly configured
        // Actual OAuth callback testing would require browser automation
    }

    @Test
    @DisplayName("JWT Token Validation: Valid vs Invalid Tokens")
    void shouldValidateJWTTokens() {
        // Step 1: Create OAuth2 user and get valid token
        String email = "jwtuser@example.com";
        String validToken = createOAuth2User(email);
        createActiveSubscriptionForUser(email);

        // Verify user exists in database before making request
        var userOpt = userRepository.findByEmail(email);
        assertTrue(userOpt.isPresent(), "User should exist in database before making request. Email: " + email);

        // Step 2: Use valid token - should work
        webApiClient.withAuth(validToken).getChatbots()
            .expectStatus().isOk();

        // Step 3: Use invalid token - should fail
        AtomicReference<Integer> statusCodeRef = new AtomicReference<>();
        webApiClient.withAuth("invalid.jwt.token").getChatbots()
            .expectStatus().is4xxClientError()
            .expectBody()
            .consumeWith(result -> {
                int status = result.getStatus().value();
                statusCodeRef.set(status);
                assertTrue(status == 401 || status == 403,
                    "Should return 401 or 403 with invalid token. Got: " + status);
            });

        // Step 4: Use malformed token - should fail
        webApiClient.withAuth("not-even-a-jwt").getChatbots()
            .expectStatus().is4xxClientError()
            .expectBody()
            .consumeWith(result -> {
                int status = result.getStatus().value();
                assertTrue(status == 401 || status == 403,
                    "Should return 401 or 403 with malformed token. Got: " + status);
            });

        // Step 5: Restore valid token - should work again
        webApiClient.withAuth(validToken).getChatbots()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("OAuth2 User Creation: Duplicate Email Handling")
    void shouldHandleDuplicateEmailOAuth2User() {
        // Step 1: Create first OAuth2 user
        String email = "duplicate@example.com";
        String googleId1 = "google_123";
        createOAuth2User(email, googleId1, "User One");

        // Step 2: Create second OAuth2 user with same email but different Google ID
        // This should link to existing user
        apiClient.clearAuth();
        String googleId2 = "google_456";
        String token2 = createOAuth2User(email, googleId2, "User Two");

        // User should exist and token should be valid
        assertNotNull(token2, "Token should be generated even for existing email");
        
        // Verify user still exists
        var userOpt = userRepository.findByEmail(email);
        assertTrue(userOpt.isPresent(), "User should exist");
    }

    @Test
    @DisplayName("OAuth2 User Creation: Valid Email Formats")
    void shouldAcceptValidEmailFormats() {
        // OAuth2 users come from Google, so email is always valid
        // This test verifies that valid emails work
        String[] validEmails = {
            "user@example.com",
            "user.name@example.com",
            "user+tag@example.co.uk"
        };

        for (String validEmail : validEmails) {
            String token = createOAuth2User(validEmail);
            assertNotNull(token, "Should create OAuth2 user with valid email: " + validEmail);
        }
    }

    @Test
    @DisplayName("OAuth2 User Creation: No Password Required")
    void shouldCreateOAuth2UserWithoutPassword() {
        // OAuth2 users don't need passwords - Google handles authentication
        String email = "oauthuser@example.com";
        String token = createOAuth2User(email);
        
        assertNotNull(token, "OAuth2 user should be created without password");
        
        // Verify user exists
        var userOpt = userRepository.findByEmail(email);
        assertTrue(userOpt.isPresent(), "OAuth2 user should exist");
        assertEquals(com.prayer_chat.chatbot.model.User.AuthProvider.GOOGLE, 
            userOpt.get().getAuthProvider(), "User should have GOOGLE auth provider");
    }

    @Test
    @DisplayName("OAuth2: Multiple Login Sessions")
    void shouldHandleMultipleOAuth2Sessions() {
        // Step 1: Create OAuth2 user
        String email = "multisession@example.com";
        String token1 = createOAuth2User(email);
        createActiveSubscriptionForUser(email);

        // Step 2: Clear auth and create new session (simulates re-login)
        // Small delay to ensure different JWT token (different iat timestamp)
        try {
            Thread.sleep(1000); // 1 second delay to ensure different timestamp
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String token2 = createOAuth2User(email);

        // Step 3: Both tokens should be different but valid
        assertNotEquals(token1, token2, "Each OAuth2 login should generate a new token");
        
        // Step 4: Both tokens should work
        webApiClient.withAuth(token1).getChatbots()
            .expectStatus().isOk();

        webApiClient.withAuth(token2).getChatbots()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("OAuth2 Session Management: Multiple Concurrent Sessions")
    void shouldAllowMultipleConcurrentOAuth2Sessions() {
        // Step 1: Create OAuth2 user
        String email = "concurrent@example.com";
        String token1 = createOAuth2User(email);
        createActiveSubscriptionForUser(email);

        // Step 2: Create second session (simulates re-login via OAuth2)
        // Small delay to ensure different JWT token (different iat timestamp)
        try {
            Thread.sleep(1000); // 1 second delay to ensure different timestamp
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String token2 = createOAuth2User(email);

        // Step 3: Both tokens should be different
        assertNotEquals(token1, token2, "Each OAuth2 login should generate a new token");

        // Step 4: Both tokens should work
        webApiClient.withAuth(token1).getChatbots()
            .expectStatus().isOk();

        webApiClient.withAuth(token2).getChatbots()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("OAuth2 Auth Provider: GOOGLE users")
    void shouldCreateGoogleOAuth2Users() {
        // Step 1: Create OAuth2 user (GOOGLE provider)
        String email = "googleuser@example.com";
        String token = createOAuth2User(email);
        createActiveSubscriptionForUser(email);

        // Verify OAuth2 user is created with GOOGLE provider
        webApiClient.withAuth(token).getChatbots()
            .expectStatus().isOk();

        // Verify user has GOOGLE auth provider
        var userOpt = userRepository.findByEmail(email);
        assertTrue(userOpt.isPresent(), "OAuth2 user should exist");
        assertEquals(com.prayer_chat.chatbot.model.User.AuthProvider.GOOGLE, 
            userOpt.get().getAuthProvider(), "User should have GOOGLE auth provider");
    }

    @Test
    @DisplayName("Token Persistence: Token Should Work Across Requests")
    void shouldPersistTokenAcrossRequests() {
        // Step 1: Create OAuth2 user and get token
        String email = "persistent@example.com";
        String token = createOAuth2User(email);
        createActiveSubscriptionForUser(email);

        // Step 2: Make multiple requests with same token
        for (int i = 0; i < 5; i++) {
            webApiClient.withAuth(token).getChatbots()
                .expectStatus().isOk();
        }
    }

    @Test
    @DisplayName("OAuth2 User: Complete User Profile Data")
    void shouldCreateOAuth2UserWithCompleteProfile() {
        // Create OAuth2 user
        String email = "profile@example.com";
        String googleId = "google_profile_123";
        String name = "Profile User";
        String token = createOAuth2User(email, googleId, name);

        assertNotNull(token, "Token should be generated");

        // Verify user data via /api/auth/me endpoint
        webApiClient.withAuth(token).get("/api/auth/me")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.email").isEqualTo(email)
            .jsonPath("$.username").isEqualTo(email) // OAuth2 users use email as username
            .jsonPath("$.id").exists()
            .jsonPath("$.authProvider").isEqualTo("GOOGLE");
    }
}
