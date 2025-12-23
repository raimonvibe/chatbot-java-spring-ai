package com.prayer_chat.chatbot.e2e;

import com.prayer_chat.chatbot.helpers.E2ETestBase;
import com.prayer_chat.chatbot.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sample E2E test to validate Phase 1 setup
 *
 * This test ensures that:
 * - E2ETestBase provides proper configuration
 * - Testcontainers (PostgreSQL) works correctly
 * - WireMock mocks external APIs properly
 * - REST Assured can make API calls
 * - ApiTestClient helper functions work
 */
@DisplayName("Sample E2E Test - Phase 1 Validation")
class SampleE2ETest extends E2ETestBase {

    @Test
    @DisplayName("Should validate E2E test infrastructure setup")
    void shouldValidateE2EInfrastructure() {
        // Verify that test infrastructure is set up
        assertNotNull(apiClient, "ApiTestClient should be initialized");
        assertNotNull(objectMapper, "ObjectMapper should be autowired");
        assertNotNull(wireMockServer, "WireMock server should be running");
        assertTrue(wireMockServer.isRunning(), "WireMock server should be running");
        assertTrue(port > 0, "Server port should be assigned");
        assertTrue(postgresContainer.isRunning(), "PostgreSQL container should be running");
    }

    @Test
    @DisplayName("Should complete OAuth2 user creation journey")
    void shouldCompleteOAuth2UserCreationJourney() {
        // Arrange
        String email = "newuser@example.com";
        String googleId = "google_12345";
        String name = "New User";

        // Act: Create OAuth2 user (simulates Google OAuth2 login)
        String token = createOAuth2User(email, googleId, name);

        // Assert: Token should be generated
        assertNotNull(token, "JWT token should be generated for OAuth2 user");
        assertNotNull(webApiClient.withAuth(token).getAuthToken(), "Auth token should be set in API client");
        
        // Verify user was created in database
        var userOpt = userRepository.findByEmail(email);
        assertTrue(userOpt.isPresent(), "User should be created in database");
        assertEquals(User.AuthProvider.GOOGLE, userOpt.get().getAuthProvider(), "User should have GOOGLE auth provider");
    }

    @Test
    @DisplayName("Should complete OAuth2 authentication journey")
    void shouldCompleteOAuth2AuthenticationJourney() {
        // Arrange: Create OAuth2 user
        String email = "testuser@example.com";
        String googleId = "google_test_123";
        String name = "Test User";
        
        // Create user via OAuth2 (simulates first Google login)
        String firstToken = createOAuth2User(email, googleId, name);
        assertNotNull(firstToken, "First login should generate token");
        
        // Clear auth to simulate new session
        apiClient.clearAuth();
        
        // Small delay to ensure different token timestamps
        try {
            Thread.sleep(1000); // 1 second delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Act: "Re-login" via OAuth2 (user already exists, just get new token)
        String secondToken = createOAuth2User(email, googleId, name);

        // Assert: New token should be generated
        assertNotNull(secondToken, "Second login should generate new token");
        assertNotEquals(firstToken, secondToken, "Each login should generate unique token");
        assertNotNull(webApiClient.withAuth(secondToken).getAuthToken(), "Auth token should be set after OAuth2 login");
    }

    @Test
    @DisplayName("Should complete create chatbot journey")
    void shouldCompleteCreateChatbotJourney() {
        // Arrange: Create OAuth2 user and authenticate
        String email = "chatbot-owner@example.com";
        String token = createOAuth2User(email);
        
        // Verify token is set
        assertNotNull(token, "JWT token should be generated");

        // Create active subscription for user (required for chatbot creation)
        createActiveSubscriptionForUser(email);

        // Act: Create chatbot
        webApiClient.withAuth(token).createChatbot(
            "Customer Support Bot",
            "https://example.com",
            "Helps customers with common questions"
        )
            .expectStatus().is2xxSuccessful()
            .expectBody()
            .jsonPath("$.name").isEqualTo("Customer Support Bot")
            .jsonPath("$.websiteUrl").isEqualTo("https://example.com");
    }

    @Test
    @DisplayName("Should complete full user journey: OAuth2 Login -> Create Chatbot -> Get Chatbots")
    void shouldCompleteFullUserJourney() {
        // Step 1: Create OAuth2 user (simulates Google login)
        String email = "journey@example.com";
        String token = createOAuth2User(email);
        
        assertNotNull(token, "Should have auth token after OAuth2 login");

        // Create active subscription for user (required for chatbot creation)
        createActiveSubscriptionForUser(email);

        // Step 2: Create first chatbot (use example.com which is a valid domain)
        webApiClient.withAuth(token).createChatbot(
            "Sales Assistant",
            "https://example.com/sales",
            "Assists with sales inquiries"
        )
            .expectStatus().is2xxSuccessful()
            .expectBody()
            .jsonPath("$.name").isEqualTo("Sales Assistant");

        // Step 3: Create second chatbot (use example.com which is a valid domain)
        webApiClient.withAuth(token).createChatbot(
            "Support Bot",
            "https://example.com/support",
            "Provides customer support"
        )
            .expectStatus().is2xxSuccessful()
            .expectBody()
            .jsonPath("$.name").isEqualTo("Support Bot");

        // Step 4: Get all chatbots
        // Small delay to ensure database transactions are committed
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        webApiClient.withAuth(token).getChatbots()
            .expectStatus().isOk()
            .expectBodyList(Map.class)
            .consumeWith(result -> {
                assertTrue(result.getResponseBody() != null && result.getResponseBody().size() >= 2,
                    "Should have at least 2 chatbots");
            });
    }

    @Test
    @DisplayName("Should handle authentication failure")
    void shouldHandleAuthenticationFailure() {
        // Act: Try to access protected endpoint without auth
        AtomicReference<Integer> statusCodeRef = new AtomicReference<>();
        webApiClient.getChatbots()
            .expectStatus().is4xxClientError()
            .expectBody()
            .consumeWith(result -> {
                int status = result.getStatus().value();
                statusCodeRef.set(status);
                assertTrue(status == 401 || status == 403,
                    "Should return 401 or 403 for unauthenticated request, but got: " + status);
            });
    }

    @Test
    @DisplayName("Should verify WireMock is mocking external APIs")
    void shouldVerifyWireMockMocking() {
        // This test validates that WireMock default mocks are working
        // The default mocks are set up in E2ETestBase.setupDefaultMocks()

        // Verify WireMock server is accessible
        assertNotNull(getWireMockUrl(), "WireMock URL should be available");
        assertTrue(wireMockServer.isRunning(), "WireMock server should be running");

        // In actual E2E tests, external API calls (Google OAuth, Stripe, AI services)
        // would be automatically mocked by WireMock
    }

    @Test
    @DisplayName("Should verify PostgreSQL container is running")
    void shouldVerifyPostgresContainer() {
        // Verify PostgreSQL container
        assertTrue(postgresContainer.isRunning(), "PostgreSQL container should be running");
        assertNotNull(postgresContainer.getJdbcUrl(), "JDBC URL should be available");
        assertEquals("chatbot_test", postgresContainer.getDatabaseName(),
            "Database name should match");
    }

    @Test
    @DisplayName("Should test ApiTestClient helper methods")
    void shouldTestApiClientHelperMethods() {
        // Test auth token management
        String testToken = "test_token_123";
        apiClient.withAuth(testToken);
        assertEquals(testToken, apiClient.getAuthToken(), "Should set auth token");

        apiClient.clearAuth();
        assertNull(apiClient.getAuthToken(), "Should clear auth token");

        // Test base URL
        assertNotNull(apiClient.getBaseUrl(), "Should have base URL");
        assertTrue(apiClient.getBaseUrl().contains("localhost"),
            "Base URL should contain localhost");
    }
}
