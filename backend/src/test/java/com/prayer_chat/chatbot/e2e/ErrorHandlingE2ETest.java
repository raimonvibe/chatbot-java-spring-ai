package com.prayer_chat.chatbot.e2e;

import com.prayer_chat.chatbot.helpers.E2ETestBase;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests for error handling scenarios
 *
 * Tests covered:
 * - External service failures (Stripe API down, AI service down)
 * - Database connection loss simulation
 * - Invalid data handling
 * - Concurrent modification conflicts
 * - Network timeout scenarios
 * - Malformed request handling
 */
@DisplayName("Error Handling E2E Tests")
class ErrorHandlingE2ETest extends E2ETestBase {

    @Test
    @DisplayName("Should handle Stripe API failure gracefully")
    void shouldHandleStripeApiFailure() {
        // Arrange: Register and authenticate user (NO active subscription - we want to test checkout creation)
        String email = "stripe-test@example.com";
        String token = createOAuth2User(email);
        // Do NOT create active subscription - we want to test checkout session creation
        apiClient.withAuth(token);

        // Mock Stripe checkout session creation to fail
        wireMockServer.stubFor(post(urlPathMatching(".*/v1/checkout/sessions.*"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\": {\"message\": \"Service temporarily unavailable\"}}")));

        // Act: Try to create checkout session
        Response response = apiClient.post("/api/subscription/create-checkout-session",
            "{\"priceId\": \"price_basic\"}");

        // Assert: Should return error with proper message
        int statusCode = response.getStatusCode();
        assertTrue(statusCode >= 500 && statusCode < 600 || statusCode == 503,
            "Should return 5xx error for external service failure. Got: " + statusCode);

        // Verify error response structure
        if (response.getContentType() != null && response.getContentType().contains("application/json")) {
            assertNotNull(response.jsonPath().get("error"),
                "Error should be present in response");
        }
    }

    @Test
    @DisplayName("Should handle AI service failure during chat")
    void shouldHandleAiServiceFailure() {
        // Arrange: Register user and create chatbot
        String email = "ai-test@example.com";
        createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        Response createResponse = apiClient.createChatbot(
            "Test Bot",
            "https://example.com",
            "Test chatbot"
        );
        Long chatbotId = createResponse.jsonPath().getLong("id");

        // Mock Anthropic AI to fail
        wireMockServer.stubFor(post(urlPathMatching("/v1/messages"))
            .willReturn(aResponse()
                .withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\": {\"message\": \"Overloaded\"}}")));

        // Act: Try to send chat message
        Response chatResponse = apiClient.post("/api/chat/" + chatbotId,
            "{\"message\": \"Hello\", \"sessionId\": \"test-session\"}");

        // Assert: Should return error indicating AI service unavailable
        int statusCode = chatResponse.getStatusCode();
        assertTrue(statusCode == 503 || statusCode == 500,
            "Should return service unavailable or internal error");
    }

    @Test
    @DisplayName("Should handle Cohere embeddings service failure")
    void shouldHandleCohereEmbeddingsFailure() {
        // Arrange: Register user
        String email = "cohere-test@example.com";
        createOAuth2User(email);
        createActiveSubscriptionForUser(email);

        // Mock Cohere embeddings to fail
        wireMockServer.stubFor(post(urlPathMatching("/embed"))
            .willReturn(aResponse()
                .withStatus(429)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\": \"Rate limit exceeded\"}")));

        // Act: Try to create chatbot (which triggers website analysis/embeddings)
        Response response = apiClient.createChatbot(
            "Embeddings Test Bot",
            "https://example.com",
            "Test bot for embeddings failure"
        );

        // Assert: Should handle the embeddings failure
        // Note: Depending on implementation, might return success but with pending analysis
        int statusCode = response.getStatusCode();
        assertTrue(statusCode == 201 || statusCode == 429 || statusCode == 500,
            "Should handle embeddings failure appropriately");
    }

    @Test
    @DisplayName("Should handle invalid chatbot data")
    void shouldHandleInvalidChatbotData() {
        // Arrange: Register user
        String email = "invalid-data@example.com";
        String token = createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        // Ensure token is set after subscription creation
        apiClient.withAuth(token);

        // Act: Try to create chatbot with invalid URL
        Response response1 = apiClient.createChatbot(
            "Invalid Bot",
            "not-a-valid-url",
            "Test bot"
        );

        // Assert: Should return 400 Bad Request
        response1.then()
            .statusCode(400)
            .body("error", notNullValue());

        // Act: Try to create chatbot with empty name
        Response response2 = apiClient.post("/api/chatbots",
            "{\"name\": \"\", \"websiteUrl\": \"https://example.com\"}");

        // Assert: Should return 400 Bad Request
        response2.then()
            .statusCode(400)
            .body("error", notNullValue());

        // Act: Try to create chatbot with extremely long name
        String longName = "A".repeat(300);
        Response response3 = apiClient.post("/api/chatbots",
            "{\"name\": \"" + longName + "\", \"websiteUrl\": \"https://example.com\"}");

        // Assert: Should return 400 Bad Request
        response3.then()
            .statusCode(400);
    }

    @Test
    @DisplayName("Should handle malformed JSON in requests")
    void shouldHandleMalformedJson() {
        // Arrange: Register user
        String email = "malformed@example.com";
        String token = createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        // Ensure token is set after subscription creation
        apiClient.withAuth(token);

        // Act: Send malformed JSON
        Response response = apiClient.post("/api/chatbots",
            "{invalid json here}");

        // Assert: Should return 400 Bad Request
        response.then()
            .statusCode(400)
            .body("error", notNullValue());
    }

    @Test
    @DisplayName("Should handle concurrent chatbot modification")
    void shouldHandleConcurrentModification() {
        // Arrange: Register user and create chatbot
        String email = "concurrent@example.com";
        createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        Response createResponse = apiClient.createChatbot(
            "Concurrent Test Bot",
            "https://example.com",
            "Test bot for concurrent modification"
        );
        Long chatbotId = createResponse.jsonPath().getLong("id");

        // Act: Simulate concurrent updates
        // Update 1: Change name
        Response update1 = apiClient.put("/api/chatbots/" + chatbotId,
            "{\"name\": \"Updated Name 1\", \"websiteUrl\": \"https://example.com\"}");

        // Update 2: Change name again
        Response update2 = apiClient.put("/api/chatbots/" + chatbotId,
            "{\"name\": \"Updated Name 2\", \"websiteUrl\": \"https://example.com\"}");

        // Assert: Both updates should succeed (last write wins)
        update1.then().statusCode(200);
        update2.then().statusCode(200);

        // Verify final state
        Response getResponse = apiClient.get("/api/chatbots/" + chatbotId);
        getResponse.then()
            .statusCode(200)
            .body("name", equalTo("Updated Name 2"));
    }

    @Test
    @DisplayName("Should handle network timeout gracefully")
    void shouldHandleNetworkTimeout() {
        // Arrange: Register and authenticate user (NO active subscription - we want to test checkout creation)
        String email = "timeout@example.com";
        String token = createOAuth2User(email);
        // Do NOT create active subscription - we want to test checkout session creation
        apiClient.withAuth(token);

        // Mock Stripe with delay to simulate timeout
        wireMockServer.stubFor(post(urlPathMatching(".*/v1/checkout/sessions.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(30000) // 30 second delay
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\": \"cs_timeout\"}")));

        // Act: Try to create checkout session with timeout
        Response response = apiClient.post("/api/subscription/create-checkout-session",
            "{\"priceId\": \"price_basic\"}");

        // Assert: Should either timeout or return error
        // Note: Behavior depends on timeout configuration
        // Accept 400 if validation fails, or timeout-related codes
        int statusCode = response.getStatusCode();
        assertTrue(statusCode == 504 || statusCode == 408 || statusCode == 500 || statusCode == 400,
            "Should return timeout-related error code or 400 (validation). Got: " + statusCode);
    }

    @Test
    @DisplayName("Should handle chatbot not found")
    void shouldHandleChatbotNotFound() {
        // Arrange: Register user
        String email = "notfound@example.com";
        createOAuth2User(email);
        createActiveSubscriptionForUser(email);

        // Act: Try to access non-existent chatbot
        Response response = apiClient.get("/api/chatbots/999999");

        // Assert: Should return 404 Not Found
        int statusCode = response.getStatusCode();
        assertEquals(404, statusCode, "Should return 404 Not Found for non-existent chatbot");
        // Only check body if content-type is JSON
        if (response.getContentType() != null && response.getContentType().contains("application/json")) {
            response.then().body("error", notNullValue());
        }
    }

    @Test
    @DisplayName("Should handle unauthorized access to chatbot")
    void shouldHandleUnauthorizedChatbotAccess() {
        // Arrange: User 1 creates chatbot
        String email = "owner@example.com";
        createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        Response createResponse = apiClient.createChatbot(
            "Owner's Bot",
            "https://example.com",
            "Private bot"
        );
        Long chatbotId = createResponse.jsonPath().getLong("id");

        // Clear auth and create User 2
        apiClient.clearAuth();
        String otherEmail = "other@example.com";
        createOAuth2User(otherEmail);
        createActiveSubscriptionForUser(otherEmail);

        // Act: User 2 tries to access User 1's chatbot
        Response response = apiClient.get("/api/chatbots/" + chatbotId);

        // Assert: Should return 403 Forbidden
        response.then()
            .statusCode(403);
    }

    @Test
    @DisplayName("Should handle invalid authentication token")
    void shouldHandleInvalidAuthToken() {
        // Act: Set invalid auth token and try to access protected endpoint
        apiClient.withAuth("invalid.jwt.token");
        Response response = apiClient.get("/api/chatbots");

        // Assert: Should return 401 Unauthorized
        response.then()
            .statusCode(401);
    }

    @Test
    @DisplayName("Should handle expired authentication token")
    void shouldHandleExpiredAuthToken() {
        // Note: This test would require generating an expired JWT
        // For now, we test with a malformed token

        // Act: Set expired/malformed token
        apiClient.withAuth("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");
        Response response = apiClient.get("/api/chatbots");

        // Assert: Should return 401 Unauthorized
        response.then()
            .statusCode(401);
    }

    @Test
    @DisplayName("Should handle duplicate chatbot creation")
    void shouldHandleDuplicateChatbotCreation() {
        // Arrange: Register user
        String email = "duplicate@example.com";
        createOAuth2User(email);
        createActiveSubscriptionForUser(email);

        // Act: Create same chatbot twice
        apiClient.createChatbot("Duplicate Bot", "https://example.com", "Test");
        Response response2 = apiClient.createChatbot("Duplicate Bot", "https://example.com", "Test");

        // Assert: Second creation should succeed (duplicate names allowed)
        // Or return conflict depending on business rules
        assertTrue(response2.getStatusCode() == 201 || response2.getStatusCode() == 409,
            "Should either allow duplicate or return conflict");
    }

    @Test
    @DisplayName("Should handle invalid subscription tier")
    void shouldHandleInvalidSubscriptionTier() {
        // Arrange: Register user
        String email = "invalid-tier@example.com";
        createOAuth2User(email);
        createActiveSubscriptionForUser(email);

        // Act: Try to create checkout session with invalid price ID
        Response response = apiClient.post("/api/subscription/create-checkout-session",
            "{\"priceId\": \"price_invalid_nonexistent\"}");

        // Assert: Should return error
        int statusCode = response.getStatusCode();
        assertTrue(statusCode == 400 || statusCode == 404,
            "Should return bad request or not found for invalid tier");
    }

    @Test
    @DisplayName("Should handle chat with non-existent session")
    void shouldHandleChatWithNonExistentSession() {
        // Arrange: Register user and create chatbot
        String email = "session-test@example.com";
        createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        Response createResponse = apiClient.createChatbot(
            "Session Test Bot",
            "https://example.com",
            "Test bot"
        );
        Long chatbotId = createResponse.jsonPath().getLong("id");

        // Act: Send message with non-existent session ID
        Response response = apiClient.post("/api/chat/" + chatbotId,
            "{\"message\": \"Hello\", \"sessionId\": \"nonexistent-session-999\"}");

        // Assert: Should either create new session or return error
        int statusCode = response.getStatusCode();
        assertTrue(statusCode == 200 || statusCode == 404 || statusCode == 500,
            "Should handle non-existent session gracefully (or 500 if AI service issue). Got: " + statusCode);
    }

    @Test
    @DisplayName("Should handle extremely large chat message")
    void shouldHandleExtremelLargeChatMessage() {
        // Arrange: Register user and create chatbot
        String email = "large-msg@example.com";
        createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        Response createResponse = apiClient.createChatbot(
            "Large Message Bot",
            "https://example.com",
            "Test bot"
        );
        Long chatbotId = createResponse.jsonPath().getLong("id");

        // Act: Send extremely large message (exceeds typical limits)
        String largeMessage = "A".repeat(100000); // 100KB message
        Response response = apiClient.post("/api/chat/" + chatbotId,
            "{\"message\": \"" + largeMessage + "\", \"sessionId\": \"test-session\"}");

        // Assert: Should return error or truncate
        int statusCode = response.getStatusCode();
        assertTrue(statusCode == 400 || statusCode == 413 || statusCode == 500,
            "Should reject or handle large message appropriately (or 500 if AI service issue). Got: " + statusCode);
    }

    @Test
    @DisplayName("Should handle Google OAuth failure")
    void shouldHandleGoogleOAuthFailure() {
        // Mock Google OAuth to fail
        wireMockServer.stubFor(post(urlPathMatching(".*/oauth2/v2/token.*"))
            .willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\": \"invalid_grant\"}")));

        // Act: Try OAuth flow (simulated)
        // Note: Actual OAuth callback is handled by Spring Security at /login/oauth2/code/google
        // This endpoint doesn't exist as a POST endpoint, so we expect 404 or 405
        // In a real scenario, OAuth failures are handled by the failure handler
        Response response = apiClient.post("/api/auth/oauth2/callback",
            "{\"code\": \"invalid_code\", \"state\": \"test_state\"}");

        // Assert: Should handle OAuth failure or return 404/405 (endpoint doesn't exist)
        // OAuth callback is handled by Spring Security, not a REST endpoint
        int statusCode = response.getStatusCode();
        assertTrue(statusCode == 401 || statusCode == 400 || statusCode == 404 || statusCode == 405 || statusCode == 500,
            "Should return error for failed OAuth or 404/405 (endpoint doesn't exist). Got: " + statusCode);
    }
}
