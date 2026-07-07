package com.prayer_chat.chatbot.dto;

import com.prayer_chat.chatbot.model.Chatbot;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API response DTO for chatbot resources. Decouples the REST contract from JPA entities
 * so controllers do not need {@code @Transactional} for lazy-loading during serialization.
 */
public record ChatbotResponse(
    Long id,
    String name,
    String websiteUrl,
    String description,
    String primaryLanguage,
    List<String> supportedLanguages,
    String customPrompt,
    String brandingConfig,
    String avatarId,
    String webhookUrl,
    List<String> webhookEvents,
    String quickReplies,
    String bibleVerse,
    Boolean christianMessagingEnabled,
    Boolean jesusTeachingsEnabled,
    Boolean isActive,
    String embedCode,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static ChatbotResponse from(Chatbot chatbot) {
        if (chatbot == null) {
            return null;
        }
        return new ChatbotResponse(
            chatbot.getId(),
            chatbot.getName(),
            chatbot.getWebsiteUrl(),
            chatbot.getDescription(),
            chatbot.getPrimaryLanguage(),
            chatbot.getSupportedLanguages() != null ? List.copyOf(chatbot.getSupportedLanguages()) : List.of(),
            chatbot.getCustomPrompt(),
            chatbot.getBrandingConfig(),
            chatbot.getAvatarId(),
            chatbot.getWebhookUrl(),
            chatbot.getWebhookEvents() != null ? List.copyOf(chatbot.getWebhookEvents()) : List.of(),
            chatbot.getQuickReplies(),
            chatbot.getBibleVerse(),
            chatbot.getChristianMessagingEnabled(),
            chatbot.getJesusTeachingsEnabled(),
            chatbot.getIsActive(),
            chatbot.getEmbedCode(),
            chatbot.getCreatedAt(),
            chatbot.getUpdatedAt()
        );
    }
}
