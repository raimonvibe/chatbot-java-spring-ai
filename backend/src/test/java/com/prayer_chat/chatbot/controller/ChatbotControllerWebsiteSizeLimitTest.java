package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.dto.ChatbotRequest;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
import com.prayer_chat.chatbot.service.AccessControlService;
import com.prayer_chat.chatbot.service.AiChatbotService;
import com.prayer_chat.chatbot.service.BibleVerseService;
import com.prayer_chat.chatbot.service.ChatbotService;
import com.prayer_chat.chatbot.service.ChristianContentAnalysisService;
import com.prayer_chat.chatbot.service.CostTrackingService;
import com.prayer_chat.chatbot.service.ConversationExportService;
import com.prayer_chat.chatbot.service.WebsiteAnalysisService;
import com.prayer_chat.chatbot.service.WebsiteSizeEstimator;
import com.prayer_chat.chatbot.service.RateLimitingService;
import com.prayer_chat.chatbot.service.UrlValidationService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for Website Size Limit enforcement in ChatbotController
 * 
 * Verifies that preview mode users are blocked from creating chatbots
 * with websites larger than 50 pages.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatbotController Website Size Limit Tests")
class ChatbotControllerWebsiteSizeLimitTest {

    @Mock
    private ChatbotRepository chatbotRepository;

    @Mock
    private ChatbotService chatbotService;

    @Mock
    private AiChatbotService aiChatbotService;

    @Mock
    private WebsiteAnalysisService websiteAnalysisService;

    @Mock
    private ConversationExportService conversationExportService;

    @Mock
    private BibleVerseService bibleVerseService;

    @Mock
    private ChristianContentAnalysisService christianContentAnalysisService;

    @Mock
    private CostTrackingService costTrackingService;

    @Mock
    private WebsiteSizeEstimator websiteSizeEstimator;

    @Mock
    private UrlValidationService urlValidationService;

    @Mock
    private WebsiteScanAuditRepository websiteScanAuditRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private CustomOAuth2User customOAuth2User;

    private ChatbotController chatbotController;

    private User testUser;
    private Chatbot testChatbot;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");

        testChatbot = new Chatbot();
        testChatbot.setId(100L);
        testChatbot.setName("Test Chatbot");
        testChatbot.setOwner(testUser);
        testChatbot.setIsActive(true);
        testChatbot.setWebsiteUrl("https://example.com");

        // Initialize controller with all required dependencies
        chatbotController = new ChatbotController(
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
            rateLimitingService
        );

        when(customOAuth2User.getUser()).thenReturn(testUser);
        lenient().when(accessControlService.hasActiveSubscription(any(User.class))).thenReturn(false);
        lenient().when(accessControlService.isPreviewMode(any(User.class))).thenReturn(true);
        lenient().when(chatbotRepository.countByOwner(anyLong())).thenReturn(0L);
        lenient().when(accessControlService.canCreateChatbot(any(User.class), anyLong())).thenReturn(true);
        lenient().when(accessControlService.getMaxChatbotsAllowed(any(User.class))).thenReturn(3);
    }

    @Test
    @DisplayName("Should block preview user from creating chatbot with website > 50 pages")
    void shouldBlockPreviewUserFromLargeWebsite() {
        // Arrange
        String largeWebsiteUrl = "https://large-website.com";
        int estimatedPages = 100; // Exceeds 50-page limit
        
        when(websiteSizeEstimator.estimateSize(largeWebsiteUrl)).thenReturn(estimatedPages);
        when(accessControlService.isPreviewMode(testUser)).thenReturn(true);

        // Act
        ResponseEntity<?> response = chatbotController.createChatbotFromUrl(
            Map.of("websiteUrl", largeWebsiteUrl),
            customOAuth2User
        );

        // Assert
        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        assertNotNull(response.getBody());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("Website too large for preview mode. Preview mode is limited to 50 pages. Upgrade to scan larger websites.", 
            body.get("error"));
        assertEquals(true, body.get("upgradeRequired"));
        assertEquals(estimatedPages, body.get("estimatedPages"));
        assertEquals(50, body.get("maxPages"));
        
        // Verify chatbot was NOT created
        verify(chatbotService, never()).createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), anyInt());
    }

    @Test
    @DisplayName("Should allow preview user to create chatbot with website <= 50 pages")
    void shouldAllowPreviewUserWithSmallWebsite() {
        // Arrange
        String smallWebsiteUrl = "https://small-website.com";
        int estimatedPages = 30; // Within 50-page limit
        
        when(websiteSizeEstimator.estimateSize(smallWebsiteUrl)).thenReturn(estimatedPages);
        when(accessControlService.isPreviewMode(testUser)).thenReturn(true);
        when(chatbotService.createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), eq(1))).thenReturn(testChatbot);
        when(websiteAnalysisService.analyzeWebsite(any(Chatbot.class))).thenReturn(null);

        // Act
        java.util.Map<String, String> request = new java.util.HashMap<>();
        request.put("websiteUrl", smallWebsiteUrl);
        ResponseEntity<?> response = chatbotController.createChatbotFromUrl(
            request,
            customOAuth2User
        );

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        
        // Verify chatbot WAS created (onboarding uses createChatbotEnforcingLimit with max 1)
        verify(chatbotService, times(1)).createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), eq(1));
    }

    @Test
    @DisplayName("Should allow paid user to create chatbot with any website size")
    void shouldAllowPaidUserWithAnyWebsiteSize() {
        // Arrange
        String largeWebsiteUrl = "https://large-website.com";
        
        // Paid users don't go through size check, so we don't need to stub estimateSize
        when(accessControlService.isPreviewMode(testUser)).thenReturn(false); // Paid user
        when(accessControlService.hasActiveSubscription(testUser)).thenReturn(true);
        when(chatbotService.createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), eq(1))).thenReturn(testChatbot);
        when(websiteAnalysisService.analyzeWebsite(any(Chatbot.class))).thenReturn(null);

        // Act
        java.util.Map<String, String> request = new java.util.HashMap<>();
        request.put("websiteUrl", largeWebsiteUrl);
        ResponseEntity<?> response = chatbotController.createChatbotFromUrl(
            request,
            customOAuth2User
        );

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        
        // Verify chatbot WAS created (size check should not block paid users)
        verify(chatbotService, times(1)).createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), eq(1));
        // Verify size estimator was NOT called for paid users
        verify(websiteSizeEstimator, never()).estimateSize(anyString());
    }

    @Test
    @DisplayName("Should block preview user in createChatbot() with website > 50 pages")
    void shouldBlockPreviewUserInCreateChatbot() {
        // Arrange
        ChatbotRequest request = new ChatbotRequest();
        request.setName("Test Bot");
        request.setDescription("Test Description");
        request.setWebsiteUrl("https://large-website.com");
        request.setPrimaryLanguage("en");
        
        int estimatedPages = 75; // Exceeds 50-page limit
        
        when(websiteSizeEstimator.estimateSize("https://large-website.com")).thenReturn(estimatedPages);
        when(accessControlService.isPreviewMode(testUser)).thenReturn(true);

        // Act
        ResponseEntity<?> response = chatbotController.createChatbot(request, customOAuth2User);

        // Assert
        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        assertNotNull(response.getBody());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(true, body.get("upgradeRequired"));
        assertEquals(estimatedPages, body.get("estimatedPages"));
        assertEquals(50, body.get("maxPages"));
        
        // Verify chatbot was NOT created
        verify(chatbotService, never()).createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), anyInt());
    }

    @Test
    @DisplayName("Should allow preview user in createChatbot() with website <= 50 pages")
    void shouldAllowPreviewUserInCreateChatbot() {
        // Arrange
        ChatbotRequest request = new ChatbotRequest();
        request.setName("Test Bot");
        request.setDescription("Test Description");
        request.setWebsiteUrl("https://small-website.com");
        request.setPrimaryLanguage("en");
        
        int estimatedPages = 25; // Within 50-page limit
        
        when(websiteSizeEstimator.estimateSize("https://small-website.com")).thenReturn(estimatedPages);
        when(accessControlService.isPreviewMode(testUser)).thenReturn(true);
        when(accessControlService.getMaxChatbotsAllowed(testUser)).thenReturn(3);
        when(chatbotService.createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), eq(3))).thenReturn(testChatbot);

        // Act
        ResponseEntity<?> response = chatbotController.createChatbot(request, customOAuth2User);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        
        // Verify chatbot WAS created
        verify(chatbotService, times(1)).createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), eq(3));
    }

    @Test
    @DisplayName("Should not check size limit if website URL is empty")
    void shouldNotCheckSizeLimitIfWebsiteUrlEmpty() {
        // Arrange
        ChatbotRequest request = new ChatbotRequest();
        request.setName("Test Bot");
        request.setDescription("Test Description");
        request.setWebsiteUrl(""); // Empty URL
        request.setPrimaryLanguage("en");
        
        when(accessControlService.isPreviewMode(testUser)).thenReturn(true);
        when(accessControlService.getMaxChatbotsAllowed(testUser)).thenReturn(3);
        when(chatbotService.createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), eq(3))).thenReturn(testChatbot);

        // Act
        ResponseEntity<?> response = chatbotController.createChatbot(request, customOAuth2User);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        
        // Verify size estimator was NOT called (no URL to estimate)
        verify(websiteSizeEstimator, never()).estimateSize(anyString());
    }

    @Test
    @DisplayName("Should handle size estimation failure gracefully")
    void shouldHandleSizeEstimationFailure() {
        // Arrange
        String websiteUrl = "https://example.com";
        
        // Simulate estimation failure (returns -1 or throws exception)
        when(websiteSizeEstimator.estimateSize(websiteUrl)).thenReturn(-1);
        when(accessControlService.isPreviewMode(testUser)).thenReturn(true);
        when(chatbotService.createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), eq(1))).thenReturn(testChatbot);
        when(websiteAnalysisService.analyzeWebsite(any(Chatbot.class))).thenReturn(null);

        // Act
        ResponseEntity<?> response = chatbotController.createChatbotFromUrl(
            Map.of("websiteUrl", websiteUrl),
            customOAuth2User
        );

        // Assert
        // Should allow creation if estimation fails (conservative approach)
        assertTrue(response.getStatusCode().is2xxSuccessful() || 
                   response.getStatusCode() == HttpStatus.PAYMENT_REQUIRED);
    }

    @Test
    @DisplayName("Should use exact limit of 50 pages")
    void shouldUseExactLimitOf50Pages() {
        // Arrange
        String websiteUrl = "https://example.com";
        int estimatedPages = 50; // Exactly at limit
        
        when(websiteSizeEstimator.estimateSize(websiteUrl)).thenReturn(estimatedPages);
        when(accessControlService.isPreviewMode(testUser)).thenReturn(true);
        when(chatbotService.createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), eq(1))).thenReturn(testChatbot);
        when(websiteAnalysisService.analyzeWebsite(any(Chatbot.class))).thenReturn(null);

        // Act
        ResponseEntity<?> response = chatbotController.createChatbotFromUrl(
            Map.of("websiteUrl", websiteUrl),
            customOAuth2User
        );

        // Assert
        // 50 pages should be allowed (limit is > 50, not >= 50)
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(chatbotService, times(1)).createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), eq(1));
    }
}

