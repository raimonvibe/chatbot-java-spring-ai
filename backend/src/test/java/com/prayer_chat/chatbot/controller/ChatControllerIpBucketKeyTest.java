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
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
        chatController = new ChatController(aiChatbotService, chatbotRepository, rateLimitingService, new BillingModeService(bp), clientIpResolver, turnstileService, new com.prayer_chat.chatbot.service.EmbedRateLimiterService(""));
    }

    @Test
    @DisplayName("Per-IP preview burst is separate per chatbotId (bucket key includes chatbot)")
    void perIpBurstSeparatePerChatbotId() {
        User owner = new User();
        owner.setId(42L);
        owner.setEmail("owner@example.com");
        owner.setRoles(new HashSet<>(Set.of("USER")));

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("email", owner.getEmail());
        attrs.put("name", "Owner");
        OAuth2User oauth2User = new DefaultOAuth2User(java.util.Collections.emptyList(), attrs, "email");
        CustomOAuth2User principal = new CustomOAuth2User(oauth2User, owner);

        Chatbot bot1 = new Chatbot();
        bot1.setId(1L);
        bot1.setIsActive(true);
        bot1.setOwner(owner);

        Chatbot bot2 = new Chatbot();
        bot2.setId(2L);
        bot2.setIsActive(true);
        bot2.setOwner(owner);

        when(chatbotRepository.findByIdWithOwner(1L)).thenReturn(Optional.of(bot1));
        when(chatbotRepository.findByIdWithOwner(2L)).thenReturn(Optional.of(bot2));

        when(httpRequest.getHeader("User-Agent")).thenReturn("test-UA");
        when(clientIpResolver.resolveClientIp(httpRequest)).thenReturn("203.0.113.9");

        when(rateLimitingService.checkIpMessageLimit("203.0.113.9"))
            .thenReturn(new RateLimitingService.IpMessageLimitResult(true, 60, 0));
        when(rateLimitingService.checkMessageLimit(any(User.class)))
            .thenReturn(new RateLimitingService.RateLimitResult(true, 100, 0, false, "message", false));

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
            ResponseEntity<Map<String, Object>> res = chatController.sendMessage(1L, request, httpRequest, principal);
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        ResponseEntity<Map<String, Object>> otherBot = chatController.sendMessage(2L, request, httpRequest, principal);
        assertThat(otherBot.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map<String, Object>> throttled = chatController.sendMessage(1L, request, httpRequest, principal);
        assertThat(throttled.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Global per-IP message limit from RateLimitingService applies before per-chatbot bucket")
    void ipMessageLimitBlocksWhenServiceDenies() {
        User admin = new User();
        admin.setId(100L);
        admin.setEmail("admin@example.com");
        admin.setRoles(new HashSet<>(Set.of("ADMIN", "USER")));

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("email", admin.getEmail());
        attrs.put("name", "Admin");
        OAuth2User oauth2User = new DefaultOAuth2User(admin.getAuthorities(), attrs, "email");
        CustomOAuth2User adminPrincipal = new CustomOAuth2User(oauth2User, admin);

        Chatbot bot = new Chatbot();
        bot.setId(1L);
        bot.setIsActive(true);
        bot.setOwner(null);

        when(chatbotRepository.findByIdWithOwner(1L)).thenReturn(Optional.of(bot));
        when(clientIpResolver.resolveClientIp(httpRequest)).thenReturn("203.0.113.9");
        when(rateLimitingService.checkIpMessageLimit("203.0.113.9"))
            .thenReturn(new RateLimitingService.IpMessageLimitResult(false, 60, 60));

        ChatRequest request = new ChatRequest("Hello", "session_1", "en");
        ResponseEntity<Map<String, Object>> ipThrottled = chatController.sendMessage(1L, request, httpRequest, adminPrincipal);

        assertThat(ipThrottled.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ipThrottled.getBody()).isNotNull();
        assertThat(ipThrottled.getBody().get("limit")).isEqualTo(60);
        assertThat(ipThrottled.getBody().get("current")).isEqualTo(60);
    }
}
