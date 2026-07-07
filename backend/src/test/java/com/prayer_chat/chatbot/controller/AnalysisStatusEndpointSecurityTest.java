package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.exception.ForbiddenException;
import com.prayer_chat.chatbot.exception.ResourceNotFoundException;
import com.prayer_chat.chatbot.exception.UnauthorizedException;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
import com.prayer_chat.chatbot.service.AccessControlService;
import com.prayer_chat.chatbot.service.ChatbotAccessService;
import com.prayer_chat.chatbot.service.ChatbotWebsiteAnalysisService;
import com.prayer_chat.chatbot.service.WebsiteAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Security: analysis-status must not be callable without auth or for other users' chatbots (same as getChatbot).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Analysis status endpoint security")
class AnalysisStatusEndpointSecurityTest {

    @Mock private ChatbotRepository chatbotRepository;
    @Mock private AccessControlService accessControlService;
    @Mock private WebsiteAnalysisService websiteAnalysisService;
    @Mock private ChatbotWebsiteAnalysisService chatbotWebsiteAnalysisService;
    @Mock private CustomOAuth2User principal;

    private ChatbotAnalysisController controller;
    private ChatbotAccessService chatbotAccessService;
    private User owner;
    private Chatbot chatbot;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(10L);
        owner.setEmail("owner@example.com");

        chatbot = new Chatbot();
        chatbot.setId(99L);
        chatbot.setOwner(owner);
        chatbot.setName("Bot");

        chatbotAccessService = new ChatbotAccessService(accessControlService, chatbotRepository);
        controller = new ChatbotAnalysisController(chatbotAccessService, chatbotWebsiteAnalysisService);
    }

    @Test
    @DisplayName("Unauthenticated request throws UnauthorizedException")
    void unauthenticatedReturns401() {
        assertThrows(UnauthorizedException.class, () -> controller.getAnalysisStatus(99L, null));
    }

    @Test
    @DisplayName("Missing chatbot throws ResourceNotFoundException")
    void missingChatbotReturns404() {
        when(principal.getUser()).thenReturn(owner);
        when(accessControlService.hasActiveSubscription(owner)).thenReturn(true);
        when(chatbotRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> controller.getAnalysisStatus(99L, principal));
    }

    @Test
    @DisplayName("Non-owner throws ForbiddenException")
    void nonOwnerReturns403() {
        User other = new User();
        other.setId(11L);
        other.setEmail("other@example.com");
        when(principal.getUser()).thenReturn(other);
        when(accessControlService.hasActiveSubscription(other)).thenReturn(true);
        when(chatbotRepository.findById(99L)).thenReturn(Optional.of(chatbot));

        assertThrows(ForbiddenException.class, () -> controller.getAnalysisStatus(99L, principal));
    }

    @Test
    @DisplayName("Owner with subscription gets status body")
    void ownerOkReturnsBody() {
        when(principal.getUser()).thenReturn(owner);
        when(accessControlService.hasActiveSubscription(owner)).thenReturn(true);
        when(chatbotRepository.findById(99L)).thenReturn(Optional.of(chatbot));
        when(chatbotWebsiteAnalysisService.getAnalysisStatus(chatbot))
            .thenReturn(Map.of("ready", true, "pagesIndexed", 3L));

        ResponseEntity<Map<String, Object>> res = controller.getAnalysisStatus(99L, principal);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(Boolean.TRUE, res.getBody().get("ready"));
        assertEquals(3L, res.getBody().get("pagesIndexed"));
    }
}
