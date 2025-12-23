package com.prayer_chat.chatbot.helpers;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * API Test Client - Wrapper around REST Assured for E2E tests
 * Provides convenient methods for making HTTP requests with authentication
 */
public class ApiTestClient {

    private final String baseUrl;
    private String authToken;

    public ApiTestClient(int port) {
        this.baseUrl = "http://localhost:" + port;
        
        // Configure REST Assured static settings
        // RestAssured will automatically use Jackson 2.x if available in classpath
        // Important: Don't call reset() as it can break HTTP client initialization
        RestAssured.baseURI = this.baseUrl;
        RestAssured.port = port;
        RestAssured.urlEncodingEnabled = false;
        
        // Ensure REST Assured is properly initialized
        // This is a workaround for a known issue where HTTP client isn't initialized
        try {
            // Force initialization by accessing the config
            RestAssured.config();
        } catch (Exception e) {
            // Ignore - config access might fail but that's okay
        }
    }

    /**
     * Set authentication token for subsequent requests
     */
    public ApiTestClient withAuth(String token) {
        this.authToken = token;
        return this;
    }

    /**
     * Clear authentication token
     */
    public ApiTestClient clearAuth() {
        this.authToken = null;
        return this;
    }

    /**
     * Get the current auth token
     */
    public String getAuthToken() {
        return authToken;
    }

    /**
     * Create a request specification with common setup
     * Uses static baseURI/port configuration (set in constructor)
     */
    private RequestSpecification createRequest() {
        // Ensure static config is set (in case it was changed)
        if (RestAssured.baseURI == null || !RestAssured.baseURI.equals(baseUrl)) {
            RestAssured.baseURI = baseUrl;
        }
        if (RestAssured.port != extractPort()) {
            RestAssured.port = extractPort();
        }
        
        // Create request specification using static config
        // This matches the pattern used in sendStripeWebhook() which works
        RequestSpecification spec = RestAssured.given()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON);

        if (authToken != null && !authToken.isEmpty()) {
            spec.header("Authorization", "Bearer " + authToken);
        }

        return spec;
    }
    
    /**
     * Extract port from baseUrl
     */
    private int extractPort() {
        try {
            String[] parts = baseUrl.split(":");
            if (parts.length >= 3) {
                return Integer.parseInt(parts[2]);
            }
        } catch (Exception e) {
            // Ignore
        }
        return 8080; // Default fallback
    }

    /**
     * GET request
     * Uses the exact same pattern as sendStripeWebhook() which works correctly
     * The key is to use createRequest() and rely on static baseURI/port configuration
     */
    public Response get(String path) {
        // Use createRequest() to ensure consistent baseUri/port configuration
        // This matches the exact pattern used in sendStripeWebhook() which works
        RequestSpecification request = createRequest();
        // Use relative path (not full URL) - REST Assured will use static baseURI/port
        return request.get(path);
    }

    /**
     * GET request with query parameters
     */
    public Response get(String path, Map<String, ?> queryParams) {
        RequestSpecification request = createRequest();
        if (request == null) {
            throw new IllegalStateException("Failed to create request specification. baseUrl: " + baseUrl);
        }
        Response response = request.queryParams(queryParams).get(path);
        if (response == null) {
            throw new IllegalStateException("REST Assured returned null response for GET " + path);
        }
        return response;
    }

    /**
     * POST request with body
     */
    public Response post(String path, Object body) {
        return createRequest()
            .body(body)
            .post(path);
    }

    /**
     * POST request without body
     */
    public Response post(String path) {
        return createRequest().post(path);
    }

    /**
     * PUT request with body
     */
    public Response put(String path, Object body) {
        return createRequest()
            .body(body)
            .put(path);
    }

    /**
     * PATCH request with body
     */
    public Response patch(String path, Object body) {
        return createRequest()
            .body(body)
            .patch(path);
    }

    /**
     * DELETE request
     */
    public Response delete(String path) {
        return createRequest().delete(path);
    }

    /**
     * Login and store auth token
     * @param email User email
     * @param password User password
     * @return Response from login request
     */
    public Response login(String email, String password) {
        Map<String, String> loginRequest = Map.of(
            "email", email,
            "password", password
        );

        Response response = post("/api/auth/login", loginRequest);

        if (response.statusCode() == 200) {
            this.authToken = response.jsonPath().getString("token");
        }

        return response;
    }

    /**
     * Register a new user
     * Note: Register endpoint doesn't return a token
     * @param email User email
     * @param username Username
     * @param password User password
     * @return Response from registration request
     */
    public Response register(String email, String username, String password) {
        Map<String, String> registerRequest = Map.of(
            "email", email,
            "username", username,
            "password", password
        );

        return post("/api/auth/register", registerRequest);
    }

    /**
     * Register a new user and automatically login to get auth token
     * Note: In E2E tests, login may fail immediately after registration due to password encoding.
     * This method attempts login but doesn't fail if it doesn't work.
     * @param email User email
     * @param username Username
     * @param password User password
     * @return Response from registration request
     */
    public Response registerAndLogin(String email, String username, String password) {
        Response registerResponse = register(email, username, password);
        
        // If registration succeeds, try to automatically login to get auth token
        // Note: This may fail in E2E tests if password encoding hasn't been flushed to DB yet
        if (registerResponse.statusCode() == 200) {
            try {
                // Small delay to ensure user is persisted
                Thread.sleep(100);
                Response loginResponse = login(email, password);
                // Token is set by login() method if successful
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // Login may fail - that's okay, tests can login explicitly if needed
            }
        }
        
        return registerResponse;
    }

    /**
     * Create a chatbot
     * @param name Chatbot name
     * @param websiteUrl Website URL to analyze
     * @param description Chatbot description
     * @return Response from chatbot creation
     */
    public Response createChatbot(String name, String websiteUrl, String description) {
        Map<String, Object> chatbotRequest = Map.of(
            "name", name,
            "websiteUrl", websiteUrl,
            "description", description,
            "primaryLanguage", "en",
            "customPrompt", "You are a helpful assistant."
        );

        return post("/api/chatbots", chatbotRequest);
    }

    /**
     * Get all chatbots for the authenticated user
     */
    public Response getChatbots() {
        return get("/api/chatbots");
    }

    /**
     * Get a specific chatbot by ID
     */
    public Response getChatbot(Long id) {
        return get("/api/chatbots/" + id);
    }

    /**
     * Delete a chatbot
     */
    public Response deleteChatbot(Long id) {
        return delete("/api/chatbots/" + id);
    }

    /**
     * Send a chat message
     * @param chatbotId ID of the chatbot
     * @param message User message
     * @return Response containing AI response
     */
    public Response sendChatMessage(Long chatbotId, String message) {
        Map<String, String> chatRequest = Map.of(
            "message", message
        );

        return post("/api/chat/" + chatbotId, chatRequest);
    }

    /**
     * Get subscription status
     */
    public Response getSubscriptionStatus() {
        return get("/api/subscription/status");
    }

    /**
     * Create Stripe checkout session
     * @param priceId Stripe price ID
     * @return Response containing checkout session URL
     */
    public Response createCheckoutSession(String priceId) {
        Map<String, String> checkoutRequest = Map.of(
            "priceId", priceId
        );

        return post("/api/subscription/create-checkout-session", checkoutRequest);
    }

    /**
     * Send a Stripe webhook event
     * @param eventType Stripe event type
     * @param payload Event payload
     * @param signature Stripe signature
     * @return Response from webhook handler
     */
    public Response sendStripeWebhook(String eventType, Object payload, String signature) {
        // Use createRequest() to ensure consistent baseUri/port configuration
        RequestSpecification request = createRequest();
        return request
            .header("Stripe-Signature", signature)
            .body(payload)
            .post("/api/webhooks/stripe");
    }

    /**
     * Get base URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }
}
