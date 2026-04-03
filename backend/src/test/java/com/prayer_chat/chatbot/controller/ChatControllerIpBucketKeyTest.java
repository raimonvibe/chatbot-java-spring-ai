package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.config.BillingProperties;
import com.prayer_chat.chatbot.dto.ChatRequest;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.security.ClientIpResolver;
import com.prayer_chat.chatbot.service.AiChatbotService;
import com.prayer_chat.chatbot.service.BillingModeService;
import com.prayer_chat.chatbot.service.RateLimitingService;
import com.prayer_chat.chatbot.service.TurnstileService;
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

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatController per-IP bucket key tests")
class ChatControllerIpBucketKeyTest {

    @Mock private ChatbotRepository chatbotRepository;
    @Mock private AiChatbotService aiChatbotService;
    @Mock private RateLimitingService rateLimitingService;
    @Mock private ClientIpResolver clientIpResolver;
    @Mock private TurnstileService turnstileService;
    @Mock private HttpServletRequest httpRequest;

    private ChatController chatController;

    @BeforeEach
    void setUp() {
        BillingProperties bp = new BillingProperties();
        bp.setEnabled(true);
        chatController = new ChatController(aiChatbotService, chatbotRepository, rateLimitingService, new BillingModeService(bp), clientIpResolver, turnstileService);
    }

    @Test
    @DisplayName("Per-IP preview throttling is separate per chatbotId (bucket key includes chatbot)")
    void perIpThrottleIsSeparatePerChatbotId() {
        Chatbot bot1 = new Chatbot();
        bot1.setId(1L);
        bot1.setIsActive(true);
        bot1.setOwner(null); // avoid owner quota checks

        Chatbot bot2 = new Chatbot();
        bot2.setId(2L);
        bot2.setIsActive(true);
        bot2.setOwner(null);

        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(bot1));
        when(chatbotRepository.findById(2L)).thenReturn(Optional.of(bot2));

        when(httpRequest.getHeader("User-Agent")).thenReturn("test-UA");
        when(clientIpResolver.resolveClientIp(httpRequest)).thenReturn("203.0.113.9");

        when(rateLimitingService.checkIpMessageLimit("203.0.113.9"))
            .thenReturn(new RateLimitingService.IpMessageLimitResult(true, 60, 0));

        ChatResponse response = mock(ChatResponse.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(response.getResult().getOutput().getText()).thenReturn("OK");

        when(aiChatbotService.processMessage(
            anyLong(),
            any(),
            any(),
            any(),
            eq("203.0.113.9"),
            eq("test-UA")
        )).thenReturn(response);

        ChatRequest request = new ChatRequest("Hello", "session_1", "en");

        for (int i = 0; i < 30; i++) {
            ResponseEntity<Map<String, Object>> res = chatController.sendMessage(1L, request, httpRequest);
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // Same IP, different chatbot: separate bucket — still allowed.
        ResponseEntity<Map<String, Object>> bot2Ok = chatController.sendMessage(2L, request, httpRequest);
        assertThat(bot2Ok.getStatusCode()).isEqualTo(HttpStatus.OK);

        // bot1 bucket exhausted for this IP
        ResponseEntity<Map<String, Object>> bot1Throttled = chatController.sendMessage(1L, request, httpRequest);
        assertThat(bot1Throttled.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Global per-IP message limit from RateLimitingService applies before per-chatbot bucket")
    void ipMessageLimitBlocksWhenServiceDenies() {
        Chatbot bot1 = new Chatbot();
        bot1.setId(1L);
        bot1.setIsActive(true);
        bot1.setOwner(null);

        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(bot1));
        when(clientIpResolver.resolveClientIp(httpRequest)).thenReturn("203.0.113.9");
        when(rateLimitingService.checkIpMessageLimit("203.0.113.9"))
            .thenReturn(new RateLimitingService.IpMessageLimitResult(false, 60, 60));

        ChatRequest request = new ChatRequest("Hello", "session_1", "en");
        ResponseEntity<Map<String, Object>> ipThrottled = chatController.sendMessage(1L, request, httpRequest);
        assertThat(ipThrottled.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ipThrottled.getBody()).isNotNull();
        assertThat(ipThrottled.getBody().get("limit")).isEqualTo(60);
        assertThat(ipThrottled.getBody().get("current")).isEqualTo(60);
    }
}
