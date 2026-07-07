package com.prayer_chat.chatbot.dto;

/**
 * Parameter object for chat message processing — replaces long parameter lists
 * in {@code AiChatbotService.processMessage(...)}.
 */
public record ChatRequestContext(
    Long chatbotId,
    String userMessage,
    String sessionId,
    String userLanguage,
    String userIp,
    String userAgent
) {
    public ChatRequestContext {
        if (chatbotId == null) {
            throw new IllegalArgumentException("chatbotId is required");
        }
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("userMessage is required");
        }
    }
}
