package com.prayer_chat.chatbot.helpers;

import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import java.util.Map;

/**
 * API Test Client using WebTestClient instead of REST Assured
 * Provides same interface as ApiTestClient but uses WebTestClient internally
 * 
 * This is a migration from REST Assured to WebTestClient to avoid GET request NPE bugs.
 * 
 * Usage:
 * <pre>
 * WebTestClientApiTestClient client = new WebTestClientApiTestClient(webTestClient);
 * client.withAuth(token)
 *     .getChatbots()
 *     .expectStatus().isOk()
 *     .expectBodyList(Map.class);
 * </pre>
 */
public class WebTestClientApiTestClient {
    
    private final WebTestClient webTestClient;
    private String authToken;
    
    public WebTestClientApiTestClient(WebTestClient webTestClient) {
        this.webTestClient = webTestClient;
    }
    
    /**
     * Set authentication token for subsequent requests
     */
    public WebTestClientApiTestClient withAuth(String token) {
        this.authToken = token;
        return this;
    }
    
    /**
     * Clear authentication token
     */
    public WebTestClientApiTestClient clearAuth() {
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
     * GET request
     */
    public WebTestClient.ResponseSpec get(String path) {
        WebTestClient.RequestBodySpec request = webTestClient.get()
            .uri(path)
            .accept(MediaType.APPLICATION_JSON);
        
        if (authToken != null && !authToken.isEmpty()) {
            request.header("Authorization", "Bearer " + authToken);
        }
        
        return request.exchange();
    }
    
    /**
     * GET request with query parameters
     */
    public WebTestClient.ResponseSpec get(String path, Map<String, ?> queryParams) {
        WebTestClient.RequestBodySpec request = webTestClient.get()
            .uri(uriBuilder -> {
                uriBuilder.path(path);
                if (queryParams != null) {
                    queryParams.forEach((key, value) -> {
                        if (value != null) {
                            uriBuilder.queryParam(key, value);
                        }
                    });
                }
                return uriBuilder.build();
            })
            .accept(MediaType.APPLICATION_JSON);
        
        if (authToken != null && !authToken.isEmpty()) {
            request.header("Authorization", "Bearer " + authToken);
        }
        
        return request.exchange();
    }
    
    /**
     * POST request with body
     */
    public WebTestClient.ResponseSpec post(String path, Object body) {
        WebTestClient.RequestBodySpec request = webTestClient.post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON);
        
        if (authToken != null && !authToken.isEmpty()) {
            request.header("Authorization", "Bearer " + authToken);
        }
        
        if (body != null) {
            request.body(BodyInserters.fromValue(body));
        }
        
        return request.exchange();
    }
    
    /**
     * POST request without body
     */
    public WebTestClient.ResponseSpec post(String path) {
        WebTestClient.RequestBodySpec request = webTestClient.post()
            .uri(path)
            .accept(MediaType.APPLICATION_JSON);
        
        if (authToken != null && !authToken.isEmpty()) {
            request.header("Authorization", "Bearer " + authToken);
        }
        
        return request.exchange();
    }
    
    /**
     * PUT request with body
     */
    public WebTestClient.ResponseSpec put(String path, Object body) {
        WebTestClient.RequestBodySpec request = webTestClient.put()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON);
        
        if (authToken != null && !authToken.isEmpty()) {
            request.header("Authorization", "Bearer " + authToken);
        }
        
        if (body != null) {
            request.body(BodyInserters.fromValue(body));
        }
        
        return request.exchange();
    }
    
    /**
     * PATCH request with body
     */
    public WebTestClient.ResponseSpec patch(String path, Object body) {
        WebTestClient.RequestBodySpec request = webTestClient.patch()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON);
        
        if (authToken != null && !authToken.isEmpty()) {
            request.header("Authorization", "Bearer " + authToken);
        }
        
        if (body != null) {
            request.body(BodyInserters.fromValue(body));
        }
        
        return request.exchange();
    }
    
    /**
     * DELETE request
     */
    public WebTestClient.ResponseSpec delete(String path) {
        WebTestClient.RequestHeadersSpec<?> request = webTestClient.delete()
            .uri(path)
            .accept(MediaType.APPLICATION_JSON);
        
        if (authToken != null && !authToken.isEmpty()) {
            request.header("Authorization", "Bearer " + authToken);
        }
        
        return request.exchange();
    }
    
    // ============================================================================
    // Convenience methods matching ApiTestClient interface
    // ============================================================================
    
    /**
     * Get all chatbots for the authenticated user
     */
    public WebTestClient.ResponseSpec getChatbots() {
        return get("/api/chatbots");
    }
    
    /**
     * Get a specific chatbot by ID
     */
    public WebTestClient.ResponseSpec getChatbot(Long id) {
        return get("/api/chatbots/" + id);
    }
    
    /**
     * Create a chatbot
     */
    public WebTestClient.ResponseSpec createChatbot(String name, String websiteUrl, String description) {
        Map<String, Object> body = Map.of(
            "name", name,
            "websiteUrl", websiteUrl,
            "description", description != null ? description : "",
            "primaryLanguage", "en",
            "customPrompt", "You are a helpful assistant."
        );
        return post("/api/chatbots", body);
    }
    
    /**
     * Update a chatbot
     */
    public WebTestClient.ResponseSpec updateChatbot(Long id, String name, String websiteUrl, String description) {
        Map<String, Object> body = Map.of(
            "name", name,
            "websiteUrl", websiteUrl,
            "description", description != null ? description : ""
        );
        return put("/api/chatbots/" + id, body);
    }
    
    /**
     * Delete a chatbot
     */
    public WebTestClient.ResponseSpec deleteChatbot(Long id) {
        return delete("/api/chatbots/" + id);
    }
    
    /**
     * Send a chat message
     */
    public WebTestClient.ResponseSpec sendChatMessage(Long chatbotId, String message) {
        Map<String, String> body = Map.of("message", message);
        return post("/api/chat/" + chatbotId, body);
    }
    
    /**
     * Get subscription status
     */
    public WebTestClient.ResponseSpec getSubscriptionStatus() {
        return get("/api/subscription/status");
    }
    
    /**
     * Create Stripe checkout session
     */
    public WebTestClient.ResponseSpec createCheckoutSession(String priceId) {
        Map<String, String> body = Map.of("priceId", priceId);
        return post("/api/subscription/create-checkout-session", body);
    }
    
    /**
     * Login and store auth token
     */
    public WebTestClient.ResponseSpec login(String email, String password) {
        Map<String, String> body = Map.of(
            "email", email,
            "password", password
        );
        
        // Note: WebTestClient doesn't automatically extract token from response
        // Caller needs to extract token manually from response body
        return post("/api/auth/login", body);
    }
    
    /**
     * Register a new user
     */
    public WebTestClient.ResponseSpec register(String email, String username, String password) {
        Map<String, String> body = Map.of(
            "email", email,
            "username", username,
            "password", password
        );
        return post("/api/auth/register", body);
    }
    
    /**
     * Send a Stripe webhook event
     */
    public WebTestClient.ResponseSpec sendStripeWebhook(String eventType, Object payload, String signature) {
        WebTestClient.RequestBodySpec request = webTestClient.post()
            .uri("/api/webhooks/stripe")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Stripe-Signature", signature);
        
        if (payload != null) {
            request.body(BodyInserters.fromValue(payload));
        }
        
        return request.exchange();
    }
}

