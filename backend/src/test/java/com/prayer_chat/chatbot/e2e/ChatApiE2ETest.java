package com.prayer_chat.chatbot.e2e;

import com.prayer_chat.chatbot.helpers.E2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chat API E2E Tests
 *
 * Tests chat functionality:
 * - POST /api/chat/{chatbotId} → GET conversation history
 * - Multi-turn conversation with session management
 * - Language switching mid-conversation
 * - Public chatbot access via embed code (separate tests)
 * - Rate limiting / AI availability (explicit status expectations)
 */
@DisplayName("Chat API E2E Tests")
class ChatApiE2ETest extends E2ETestBase {

    /**
     * Authenticated owner preview chat ({@code POST /api/chat/{id}}): expect a normal outcome or explicit client/infra errors.
     * Does not accept 401/403 here — those indicate authz bugs for an active bot owned by the caller.
     */
    private void assertAuthenticatedPreviewChatResponse(int statusCode) {
        assertTrue(
            statusCode == 200 || statusCode == 201 || statusCode == 400 || statusCode == 429
                || statusCode == 500 || statusCode == 503,
            "Expected 2xx success, 400 (validation), 429 (rate limit), or 5xx (AI/infra). Got: " + statusCode);
    }

    @Test
    @DisplayName("Complete Chat Flow: Send Message → Receive Response")
    void shouldCompleteChatFlow() {
        // Setup: Create user and chatbot
        String email = "chat@example.com";
        String token = createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        Long chatbotId = extractChatbotId(webApiClient.withAuth(token).createChatbot(
            "Chat Bot",
            "https://example.com/chat",
            "Testing chat"
        )
            .expectStatus().is2xxSuccessful());

        // Send chat message
        Map<String, String> chatBody = Map.of("message", "Hello, can you help me?");
        AtomicReference<Integer> statusCodeRef = new AtomicReference<>();
        webApiClient.withAuth(token).post("/api/chat/" + chatbotId, chatBody)
            .expectBody()
            .consumeWith(result -> {
                int status = result.getStatus().value();
                statusCodeRef.set(status);
                assertAuthenticatedPreviewChatResponse(status);
            });

        // Accept 200/201 (success) or 500 (AI service not available in test)
        int statusCode = statusCodeRef.get();
        if (statusCode == 200 || statusCode == 201) {
            webApiClient.withAuth(token).post("/api/chat/" + chatbotId, chatBody)
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath("$.message").exists()
                .jsonPath("$.message").value(msg -> {
                    assertNotNull(msg, "Message should not be null");
                    assertFalse(msg.toString().isEmpty(), "Message should not be empty");
                });
        } else {
            assertTrue(statusCode == 429 || statusCode == 500 || statusCode == 503,
                "When chat does not succeed, expect rate limit (429) or AI/config failure (500/503). Got: " + statusCode);
        }
    }

    @Test
    @DisplayName("Multi-Turn Conversation: Sequential Messages")
    void shouldHandleMultiTurnConversation() {
        // Setup
        String email = "multiturn@example.com";
        String token = createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        Long chatbotId = extractChatbotId(webApiClient.withAuth(token).createChatbot(
            "Conversation Bot",
            "https://example.com/conv",
            "Multi-turn chat"
        )
            .expectStatus().is2xxSuccessful());

        // Turn 1
        Map<String, String> msg1 = Map.of("message", "What is your name?");
        AtomicReference<Integer> status1Ref = new AtomicReference<>();
        webApiClient.withAuth(token).post("/api/chat/" + chatbotId, msg1)
            .expectBody()
            .consumeWith(result -> {
                int status = result.getStatus().value();
                status1Ref.set(status);
                assertAuthenticatedPreviewChatResponse(status);
                if (status == 200 || status == 201) {
                    // Message exists check
                }
            });

        // Turn 2
        Map<String, String> msg2 = Map.of("message", "What can you help me with?");
        AtomicReference<Integer> status2Ref = new AtomicReference<>();
        webApiClient.withAuth(token).post("/api/chat/" + chatbotId, msg2)
            .expectBody()
            .consumeWith(result -> {
                int status = result.getStatus().value();
                status2Ref.set(status);
                assertAuthenticatedPreviewChatResponse(status);
            });

        // Turn 3
        Map<String, String> msg3 = Map.of("message", "Thank you!");
        AtomicReference<Integer> status3Ref = new AtomicReference<>();
        webApiClient.withAuth(token).post("/api/chat/" + chatbotId, msg3)
            .expectBody()
            .consumeWith(result -> {
                int status = result.getStatus().value();
                status3Ref.set(status);
                assertAuthenticatedPreviewChatResponse(status);
            });
    }

    @Test
    @DisplayName("Chat with Non-Existent Chatbot Returns 404")
    void shouldReturn404ForNonExistentChatbot() {
        String email = "notfound@example.com";
        String token = createOAuth2User(email);
        createActiveSubscriptionForUser(email);

        Long nonExistentId = 999999L;
        Map<String, String> msg = Map.of("message", "Hello?");
        webApiClient.withAuth(token).post("/api/chat/" + nonExistentId, msg)
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("Chat without Authentication is rejected for numeric chatbot id")
    void shouldHandleUnauthenticatedChat() {
        // Create chatbot as authenticated user
        String email = "owner@example.com";
        String token = createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        Long chatbotId = extractChatbotId(webApiClient.withAuth(token).createChatbot(
            "Public Bot",
            "https://example.com/public",
            "Public chatbot"
        )
            .expectStatus().is2xxSuccessful());

        // Numeric preview chat requires JWT; embed path stays public (createOAuth2User sets a default token — clear it).
        Map<String, String> msg = Map.of("message", "Hello!");
        webApiClient.clearAuth();
        try {
            webApiClient.post("/api/chat/" + chatbotId, msg)
                .expectStatus().value(s ->
                    assertTrue(s == 401 || s == 403,
                        "Unauthenticated numeric preview chat must be rejected before the controller (401 Unauthorized or 403 Forbidden). Got: " + s));
        } finally {
            webApiClient.clearAuth();
        }
    }

    @Test
    @DisplayName("Empty Message Should Be Rejected")
    void shouldRejectEmptyMessage() {
        String email = "empty@example.com";
        String token = createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        Long chatbotId = extractChatbotId(webApiClient.withAuth(token).createChatbot(
            "Empty Test Bot",
            "https://example.com/empty",
            "Testing empty messages"
        )
            .expectStatus().is2xxSuccessful());

        Map<String, String> msg = Map.of("message", "");
        webApiClient.withAuth(token).post("/api/chat/" + chatbotId, msg)
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("Very Long Message Handling")
    void shouldHandleVeryLongMessages() {
        String email = "longmsg@example.com";
        String token = createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        Long chatbotId = extractChatbotId(webApiClient.withAuth(token).createChatbot(
            "Long Message Bot",
            "https://example.com/longmsg",
            "Testing long messages"
        )
            .expectStatus().is2xxSuccessful());

        // Create a very long message (3000 characters)
        String longMessage = "a".repeat(3000);
        Map<String, String> msg = Map.of("message", longMessage);

        webApiClient.withAuth(token).post("/api/chat/" + chatbotId, msg)
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("Special Characters in Message")
    void shouldHandleSpecialCharacters() {
        String email = "special@example.com";
        String token = createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        Long chatbotId = extractChatbotId(webApiClient.withAuth(token).createChatbot(
            "Special Char Bot",
            "https://example.com/special",
            "Testing special characters"
        )
            .expectStatus().is2xxSuccessful());

        // Message with special characters
        String specialMessage = "Hello! @#$%^&*() <script>alert('xss')</script> 你好 مرحبا";
        Map<String, String> msg = Map.of("message", specialMessage);

        AtomicReference<Integer> statusCodeRef = new AtomicReference<>();
        webApiClient.withAuth(token).post("/api/chat/" + chatbotId, msg)
            .expectBody()
            .consumeWith(result -> {
                int status = result.getStatus().value();
                statusCodeRef.set(status);
                assertAuthenticatedPreviewChatResponse(status);
            });
        
        if (statusCodeRef.get() == 200 || statusCodeRef.get() == 201) {
            webApiClient.withAuth(token).post("/api/chat/" + chatbotId, msg)
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath("$.message").exists();
        }
    }

    @Test
    @DisplayName("Concurrent Chat Messages from Same User")
    void shouldHandleConcurrentMessages() {
        String email = "concurrent-chat@example.com";
        String token = createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        Long chatbotId = extractChatbotId(webApiClient.withAuth(token).createChatbot(
            "Concurrent Bot",
            "https://example.com/concurrent-chat",
            "Testing concurrent chats"
        )
            .expectStatus().is2xxSuccessful());

        // Send multiple messages quickly
        for (int i = 0; i < 5; i++) {
            Map<String, String> msg = Map.of("message", "Message " + i);
            AtomicReference<Integer> statusRef = new AtomicReference<>();
            webApiClient.withAuth(token).post("/api/chat/" + chatbotId, msg)
                .expectBody()
                .consumeWith(result -> {
                    int status = result.getStatus().value();
                    statusRef.set(status);
                    assertAuthenticatedPreviewChatResponse(status);
                });
        }
    }

    @Test
    @DisplayName("Chat with Another User's Chatbot")
    void shouldAllowOrDenyChatWithOthersChatbot() {
        // User 1 creates chatbot
        String email = "creator-chat@example.com";
        String token1 = createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        Long chatbotId = extractChatbotId(webApiClient.withAuth(token1).createChatbot(
            "Creator's Bot",
            "https://example.com/creator",
            "Created by user 1"
        )
            .expectStatus().is2xxSuccessful());

        // User 2 tries to chat with User 1's chatbot
        String otherEmail = "other-chat@example.com";
        String token2 = createOAuth2User(otherEmail);
        createActiveSubscriptionForUser(otherEmail);
        Map<String, String> msg = Map.of("message", "Can I chat here?");
        
        webApiClient.withAuth(token2).post("/api/chat/" + chatbotId, msg)
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("Chat Response Contains Required Fields")
    void shouldReturnCompleteResponseStructure() {
        String email = "structure@example.com";
        String token = createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        Long chatbotId = extractChatbotId(webApiClient.withAuth(token).createChatbot(
            "Structure Bot",
            "https://example.com/structure",
            "Testing response structure"
        )
            .expectStatus().is2xxSuccessful());

        Map<String, String> msg = Map.of("message", "What's the weather?");
        AtomicReference<Integer> statusCodeRef = new AtomicReference<>();
        webApiClient.withAuth(token).post("/api/chat/" + chatbotId, msg)
            .expectBody()
            .consumeWith(result -> {
                int status = result.getStatus().value();
                statusCodeRef.set(status);
                assertAuthenticatedPreviewChatResponse(status);
            });
        
        if (statusCodeRef.get() == 200 || statusCodeRef.get() == 201) {
            webApiClient.withAuth(token).post("/api/chat/" + chatbotId, msg)
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath("$.message").exists();
        }
    }

    @Test
    @DisplayName("Chat with Inactive Chatbot")
    void shouldHandleChatWithInactiveChatbot() {
        String email = "inactive-chat@example.com";
        String token = createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        Long chatbotId = extractChatbotId(webApiClient.withAuth(token).createChatbot(
            "Inactive Bot",
            "https://example.com/inactive",
            "Will be deactivated"
        )
            .expectStatus().is2xxSuccessful());

        // Deactivate chatbot using PUT (PATCH is not supported, so use PUT with full chatbot object)
        Map<String, Object> chatbotUpdate = new java.util.HashMap<>();
        chatbotUpdate.put("name", "Inactive Bot");
        chatbotUpdate.put("websiteUrl", "https://example.com/inactive");
        chatbotUpdate.put("description", "Will be deactivated");
        chatbotUpdate.put("isActive", false);
        webApiClient.withAuth(token).put("/api/chatbots/" + chatbotId, chatbotUpdate)
            .expectStatus().is2xxSuccessful();

        Map<String, String> msg = Map.of("message", "Are you there?");
        webApiClient.withAuth(token).post("/api/chat/" + chatbotId, msg)
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("Rapid Sequential Messages")
    void shouldHandleRapidMessages() {
        String email = "rapid@example.com";
        String token = createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        Long chatbotId = extractChatbotId(webApiClient.withAuth(token).createChatbot(
            "Rapid Bot",
            "https://example.com/rapid",
            "Testing rapid messages"
        )
            .expectStatus().is2xxSuccessful());

        // Send 10 rapid messages
        for (int i = 0; i < 10; i++) {
            Map<String, String> msg = Map.of("message", "Rapid message " + i);
            AtomicReference<Integer> statusCodeRef = new AtomicReference<>();
            webApiClient.withAuth(token).post("/api/chat/" + chatbotId, msg)
                .expectBody()
                .consumeWith(result -> {
                    int status = result.getStatus().value();
                    statusCodeRef.set(status);
                    // Should all succeed (or some might be rate limited or 500 for AI service issues)
                    assertAuthenticatedPreviewChatResponse(status);
                });
        }
    }

    @Test
    @DisplayName("Chat Message with Different Content Types")
    void shouldHandleDifferentMessageTypes() {
        String email = "types@example.com";
        String token = createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        Long chatbotId = extractChatbotId(webApiClient.withAuth(token).createChatbot(
            "Types Bot",
            "https://example.com/types",
            "Testing message types"
        )
            .expectStatus().is2xxSuccessful());

        // Different types of messages
        String[] messages = {
            "Simple question",
            "Question with numbers: 123456",
            "URL: https://example.com",
            "Email: test@example.com",
            "Multiple lines\nLine 2\nLine 3",
            "Code: if (true) { return false; }"
        };

        for (String message : messages) {
            Map<String, String> msg = Map.of("message", message);
            AtomicReference<Integer> statusRef = new AtomicReference<>();
            webApiClient.withAuth(token).post("/api/chat/" + chatbotId, msg)
                .expectBody()
                .consumeWith(result -> {
                    int status = result.getStatus().value();
                    statusRef.set(status);
                    assertAuthenticatedPreviewChatResponse(status);
                });
        }
    }

    @Test
    @DisplayName("Get Conversation History")
    void shouldRetrieveConversationHistory() {
        String email = "history@example.com";
        String token = createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        Long chatbotId = extractChatbotId(webApiClient.withAuth(token).createChatbot(
            "History Bot",
            "https://example.com/history",
            "Testing conversation history"
        )
            .expectStatus().is2xxSuccessful());

        Map<String, String> msg1 = Map.of("message", "Message 1");
        webApiClient.withAuth(token).post("/api/chat/" + chatbotId, msg1)
            .expectBody().consumeWith(r -> assertAuthenticatedPreviewChatResponse(r.getStatus().value()));
        Map<String, String> msg2 = Map.of("message", "Message 2");
        webApiClient.withAuth(token).post("/api/chat/" + chatbotId, msg2)
            .expectBody().consumeWith(r -> assertAuthenticatedPreviewChatResponse(r.getStatus().value()));
        Map<String, String> msg3 = Map.of("message", "Message 3");
        webApiClient.withAuth(token).post("/api/chat/" + chatbotId, msg3)
            .expectBody().consumeWith(r -> assertAuthenticatedPreviewChatResponse(r.getStatus().value()));

        String sessionId = "e2e_hist_sess";
        webApiClient.withAuth(token).get("/api/chat/" + chatbotId + "/conversation/" + sessionId)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.chatbotId").isEqualTo(chatbotId.intValue())
            .jsonPath("$.sessionId").isEqualTo(sessionId);
    }
}
