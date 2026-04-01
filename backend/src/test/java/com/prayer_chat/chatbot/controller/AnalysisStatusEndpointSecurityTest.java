package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
import com.prayer_chat.chatbot.service.AccessControlService;
import com.prayer_chat.chatbot.service.AiChatbotService;
import com.prayer_chat.chatbot.service.BibleVerseService;
import com.prayer_chat.chatbot.service.ChatbotService;
import com.prayer_chat.chatbot.service.ChristianContentAnalysisService;
import com.prayer_chat.chatbot.service.ConversationExportService;
import com.prayer_chat.chatbot.service.CostTrackingService;
import com.prayer_chat.chatbot.service.RateLimitingService;
import com.prayer_chat.chatbot.service.UrlValidationService;
import com.prayer_chat.chatbot.service.WebsiteAnalysisService;
import com.prayer_chat.chatbot.service.WebsiteSizeEstimator;
import com.prayer_chat.chatbot.repository.WebsiteScanAuditRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Security: analysis-status must not be callable without auth or for other users' chatbots (same as getChatbot).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Analysis status endpoint security")
class AnalysisStatusEndpointSecurityTest {

    @Mock private ChatbotRepository chatbotRepository;
    @Mock private ChatbotService chatbotService;
    @Mock private AiChatbotService aiChatbotService;
    @Mock private WebsiteAnalysisService websiteAnalysisService;
    @Mock private ConversationExportService conversationExportService;
    @Mock private BibleVerseService bibleVerseService;
    @Mock private ChristianContentAnalysisService christianContentAnalysisService;
    @Mock private CostTrackingService costTrackingService;
    @Mock private WebsiteSizeEstimator websiteSizeEstimator;
    @Mock private WebsiteScanAuditRepository websiteScanAuditRepository;
    @Mock private AccessControlService accessControlService;
    @Mock private RateLimitingService rateLimitingService;
    @Mock private UrlValidationService urlValidationService;
    @Mock private CustomOAuth2User principal;

    private ChatbotController controller;
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

        controller = new ChatbotController(
            chatbotRepository,
            chatbotService,
            aiChatbotService,
            websiteAnalysisService,
            conversationExportService,
            bibleVerseService,
            christianContentAnalysisService,
            mock(com.prayer_chat.chatbot.service.JesusTeachingsService.class),
            mock(com.prayer_chat.chatbot.service.JesusVersesTaggingService.class),
            costTrackingService,
            websiteSizeEstimator,
            websiteScanAuditRepository,
            accessControlService,
            rateLimitingService,
            urlValidationService
        );

        lenient().when(urlValidationService.completeAndValidate(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Unauthenticated request returns 401")
    void unauthenticatedReturns401() {
        ResponseEntity<Map<String, Object>> res = controller.getAnalysisStatus(99L, null);
        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
    }

    @Test
    @DisplayName("Missing chatbot returns 404")
    void missingChatbotReturns404() {
        when(principal.getUser()).thenReturn(owner);
        when(chatbotRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> res = controller.getAnalysisStatus(99L, principal);
        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    @Test
    @DisplayName("Non-owner returns 403")
    void nonOwnerReturns403() {
        User other = new User();
        other.setId(11L);
        other.setEmail("other@example.com");
        when(principal.getUser()).thenReturn(other);
        when(chatbotRepository.findById(99L)).thenReturn(Optional.of(chatbot));
        when(accessControlService.hasActiveSubscription(other)).thenReturn(true);

        ResponseEntity<Map<String, Object>> res = controller.getAnalysisStatus(99L, principal);
        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    @DisplayName("Owner with subscription gets status body")
    void ownerOkReturnsBody() {
        when(principal.getUser()).thenReturn(owner);
        when(chatbotRepository.findById(99L)).thenReturn(Optional.of(chatbot));
        when(accessControlService.hasActiveSubscription(owner)).thenReturn(true);
        when(websiteAnalysisService.getAnalysisStatus(chatbot)).thenReturn(Map.of("ready", true, "pagesIndexed", 3L));

        ResponseEntity<Map<String, Object>> res = controller.getAnalysisStatus(99L, principal);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(Boolean.TRUE, res.getBody().get("ready"));
        assertEquals(3L, res.getBody().get("pagesIndexed"));
    }
}
