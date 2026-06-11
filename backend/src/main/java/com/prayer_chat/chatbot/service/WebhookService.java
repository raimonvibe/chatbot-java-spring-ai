package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.Conversation;
import com.prayer_chat.chatbot.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NEW FEATURE: Webhook Integration Service
 * Sends real-time events to external URLs when conversations happen
 * 
 * @author Prayer-Chat Development Team
 */
@Service
public class WebhookService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final UrlValidationService urlValidationService;

    public WebhookService(UrlValidationService urlValidationService) {
        this.urlValidationService = urlValidationService;
        // SSRF protection: never follow redirects — the URL is validated once before sending,
        // and a redirect could point the request at internal/metadata endpoints after validation.
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Send webhook event asynchronously
     */
    @Async
    public void sendWebhookEvent(Chatbot chatbot, String eventType, Map<String, Object> payload) {
        String webhookUrl = chatbot.getWebhookUrl();

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return; // No webhook configured
        }

        // Check if this event type is enabled BEFORE validating URL
        List<String> webhookEvents = chatbot.getWebhookEvents();
        if (webhookEvents == null || !webhookEvents.contains(eventType)) {
            return; // This event type is not enabled
        }

        // SSRF Protection: Validate webhook URL
        if (!urlValidationService.isValidAndSafe(webhookUrl)) {
            logger.warn("Blocked unsafe webhook URL for chatbot {}: {}",
                chatbot.getId(), urlValidationService.extractDomain(webhookUrl));
            return;
        }

        try {
            // Prepare webhook payload
            Map<String, Object> webhookPayload = new HashMap<>();
            webhookPayload.put("event", eventType);
            webhookPayload.put("chatbot_id", chatbot.getId());
            webhookPayload.put("chatbot_name", chatbot.getName());
            webhookPayload.put("timestamp", System.currentTimeMillis());
            webhookPayload.put("data", payload);

            String jsonPayload = objectMapper.writeValueAsString(webhookPayload);

            // Send HTTP POST request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "PrayerChat-Webhook/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("Webhook sent successfully to {} for event {}", chatbot.getWebhookUrl(), eventType);
            } else {
                logger.warn("Webhook failed with status {} for event {}", response.statusCode(), eventType);
            }

        } catch (Exception e) {
            logger.error("Failed to send webhook for event {}: {}", eventType, e.getMessage());
        }
    }

    /**
     * Send new conversation event
     */
    public void sendNewConversationEvent(Chatbot chatbot, Conversation conversation) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("conversation_id", conversation.getId());
        payload.put("user_ip", conversation.getUserIp());
        payload.put("language", conversation.getUserLanguage());
        payload.put("created_at", conversation.getCreatedAt());

        sendWebhookEvent(chatbot, "conversation_started", payload);
    }

    /**
     * Send new message event
     */
    public void sendNewMessageEvent(Chatbot chatbot, Conversation conversation, Message message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("conversation_id", conversation.getId());
        payload.put("message_id", message.getId());
        payload.put("message_content", message.getContent());
        payload.put("is_from_user", message.getIsUserMessage());
        payload.put("timestamp", message.getCreatedAt());

        sendWebhookEvent(chatbot, "message_sent", payload);
    }

    /**
     * Send conversation ended event
     */
    public void sendConversationEndedEvent(Chatbot chatbot, Conversation conversation) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("conversation_id", conversation.getId());
        payload.put("message_count", conversation.getMessages().size());
        payload.put("duration_minutes",
            conversation.getEndedAt() != null ?
            java.time.Duration.between(conversation.getCreatedAt(), conversation.getEndedAt()).toMinutes() : 0);

        sendWebhookEvent(chatbot, "conversation_ended", payload);
    }
}
