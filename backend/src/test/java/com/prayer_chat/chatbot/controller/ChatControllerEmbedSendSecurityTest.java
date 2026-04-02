package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.config.BillingProperties;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.service.AiChatbotService;
import com.prayer_chat.chatbot.service.BillingModeService;
import com.prayer_chat.chatbot.service.RateLimitingService;
import com.prayer_chat.chatbot.dto.ChatRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Security tests for embed message endpoint:
 * - POST /api/chat/embed/{embedCode}
 * Ensures we do not accept numeric ID swapping and we reject unsafe embedCode values.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatController embed send security tests")
class ChatControllerEmbedSendSecurityTest {

    @Mock
    private ChatbotRepository chatbotRepository;

    @Mock
    private AiChatbotService aiChatbotService;

    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private HttpServletRequest httpRequest;

    private ChatController chatController;

    @BeforeEach
    void setUp() {
        BillingProperties bp = new BillingProperties();
        bp.setEnabled(true);
        chatController = new ChatController(aiChatbotService, chatbotRepository, rateLimitingService, new BillingModeService(bp));
    }

    @Test
    @DisplayName("Embed send succeeds with valid embedCode")
    void shouldSendMessageByEmbedCode() {
        // Arrange
        Chatbot bot = new Chatbot();
        bot.setId(100L);
        bot.setIsActive(true);

        when(chatbotRepository.findByEmbedCode("prayer-chat-bot-100")).thenReturn(java.util.Optional.of(bot));

        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("203.0.113.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("test-UA");

        ChatResponse response = mock(ChatResponse.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(response.getResult().getOutput().getText()).thenReturn("Prayer-Chat reply");

        when(aiChatbotService.processMessage(
                eq(100L),
                eq("Hello"),
                eq("session_1"),
                eq("en"),
                eq("203.0.113.1"),
                eq("test-UA")
        )).thenReturn(response);

        ChatRequest request = new ChatRequest("Hello", "session_1", "en");

        // Act
        ResponseEntity<java.util.Map<String, Object>> res = chatController.sendMessageByEmbedCode(
                "prayer-chat-bot-100",
                request,
                httpRequest
        );

        // Assert
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().get("message")).isEqualTo("Prayer-Chat reply");
        assertThat(res.getBody().get("sessionId")).isEqualTo("session_1");
        assertThat(res.getBody().get("chatbotId")).isEqualTo(100L);
        verify(chatbotRepository).findByEmbedCode("prayer-chat-bot-100");
        verify(chatbotRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("Embed send rejects embedCode with unsafe characters")
    void embedSendRejectsUnsafeEmbedCode() {
        // Arrange
        ChatRequest request = new ChatRequest("Hello", "session_1", "en");

        // Act
        ResponseEntity<java.util.Map<String, Object>> res = chatController.sendMessageByEmbedCode(
                "../1",
                request,
                httpRequest
        );

        // Assert
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().get("error")).isEqualTo("Invalid embed code");

        verify(chatbotRepository, never()).findByEmbedCode(any());
        verify(chatbotRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("Numeric-looking embedCode does not fall back to numeric ID")
    void embedSendDoesNotFallBackToNumericId() {
        // Arrange
        when(chatbotRepository.findByEmbedCode("100")).thenReturn(java.util.Optional.empty());

        ChatRequest request = new ChatRequest("Hello", "session_1", "en");

        // Act
        ResponseEntity<java.util.Map<String, Object>> res = chatController.sendMessageByEmbedCode(
                "100",
                request,
                httpRequest
        );

        // Assert
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(chatbotRepository, never()).findById(anyLong());
        verify(chatbotRepository).findByEmbedCode("100");
    }
}

