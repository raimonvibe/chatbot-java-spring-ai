package com.prayer_chat.chatbot.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatbotEmbedCodeGenerationTest {

    @Test
    @DisplayName("Generated embed codes are safe, random-looking, and unique")
    void embedCodeGenerationIsSafeAndRandom() {
        String code1 = Chatbot.generateNewEmbedCode();
        String code2 = Chatbot.generateNewEmbedCode();

        String prefix = "prayer-chat-bot-";
        assertNotNull(code1);
        assertNotNull(code2);
        assertTrue(code1.startsWith(prefix));
        assertTrue(code2.startsWith(prefix));

        // Only allowed characters for widget embed endpoints.
        assertTrue(code1.matches("^[a-zA-Z0-9_-]+$"));
        assertTrue(code2.matches("^[a-zA-Z0-9_-]+$"));

        String token1 = code1.substring(prefix.length());
        String token2 = code2.substring(prefix.length());

        // Not numeric-only (timestamp-based would be digits-only).
        assertFalse(token1.matches("\\d+"));
        assertFalse(token2.matches("\\d+"));

        // Extremely low chance of collision; should be unique.
        assertNotEquals(code1, code2);

        // Keep well under the backend length cap (255).
        assertTrue(code1.length() <= 255);
        assertTrue(code2.length() <= 255);
    }
}

