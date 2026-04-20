package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.config.BillingProperties;
import com.prayer_chat.chatbot.dto.ChatRequest;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.security.ClientIpResolver;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
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
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatController preview session behaviour")
class ChatControllerPreviewSessionTest {

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
        chatController = new ChatController(
            aiChatbotService, chatbotRepository, rateLimitingService, new BillingModeService(bp), clientIpResolver, turnstileService);
    }

    @Test
    @DisplayName("Numeric preview generates a new sessionId on each request when the client omits it")
    void distinctSessionIdsWhenSessionOmitted() {
        User owner = new User();
        owner.setId(7L);
        owner.setEmail("owner@example.com");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("email", owner.getEmail());
        attrs.put("name", "Owner");
        OAuth2User oauth2User = new DefaultOAuth2User(java.util.Collections.emptyList(), attrs, "email");
        CustomOAuth2User principal = new CustomOAuth2User(oauth2User, owner);

        Chatbot bot = new Chatbot();
        bot.setId(99L);
        bot.setIsActive(true);
        bot.setOwner(owner);

        when(chatbotRepository.findByIdWithOwner(99L)).thenReturn(Optional.of(bot));
        when(httpRequest.getHeader("User-Agent")).thenReturn("test-UA");
        when(clientIpResolver.resolveClientIp(httpRequest)).thenReturn("198.51.100.1");
        when(rateLimitingService.checkIpMessageLimit("198.51.100.1"))
            .thenReturn(new RateLimitingService.IpMessageLimitResult(true, 60, 0));
        when(rateLimitingService.checkMessageLimit(any(User.class)))
            .thenReturn(new RateLimitingService.RateLimitResult(true, 100, 0, false, "ok", false));

        ChatResponse response = mock(ChatResponse.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(response.getResult().getOutput().getText()).thenReturn("OK");
        when(aiChatbotService.processMessage(anyLong(), any(), any(), any(), any(), any())).thenReturn(response);

        ChatRequest noSession = new ChatRequest("Hello", null, "en");

        ResponseEntity<Map<String, Object>> res1 = chatController.sendMessage(99L, noSession, httpRequest, principal);
        ResponseEntity<Map<String, Object>> res2 = chatController.sendMessage(99L, noSession, httpRequest, principal);

        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res1.getBody()).isNotNull();
        assertThat(res2.getBody()).isNotNull();
        Object sid1 = res1.getBody().get("sessionId");
        Object sid2 = res2.getBody().get("sessionId");
        assertThat(sid1).isNotNull().isInstanceOf(String.class);
        assertThat(sid2).isNotNull().isInstanceOf(String.class);
        assertThat(sid1).isNotEqualTo(sid2);
    }
}
