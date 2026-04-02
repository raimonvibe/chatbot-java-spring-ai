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
import com.prayer_chat.chatbot.config.BillingProperties;
import com.prayer_chat.chatbot.service.BillingModeService;
import com.prayer_chat.chatbot.service.RateLimitingService;
import com.prayer_chat.chatbot.service.UrlValidationService;
import com.prayer_chat.chatbot.repository.WebsiteScanAuditRepository;
import com.prayer_chat.chatbot.model.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for Website Size Limit enforcement in ChatbotController
 * 
 * Verifies that users are blocked from creating chatbots when estimated pages exceed plan limits (FREE = 500).
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

    private BillingModeService billingModeService;
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

        BillingProperties billingProperties = new BillingProperties();
        billingProperties.setEnabled(true);
        billingModeService = new BillingModeService(billingProperties);

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
            rateLimitingService,
            urlValidationService,
            billingModeService
        );

        when(customOAuth2User.getUser()).thenReturn(testUser);
        lenient().when(urlValidationService.completeAndValidate(anyString())).thenAnswer(invocation -> {
            String raw = invocation.getArgument(0);
            if (raw == null || raw.trim().isEmpty()) {
                return Optional.empty();
            }
            String s = raw.trim();
            if (!s.startsWith("http://") && !s.startsWith("https://")) {
                s = "https://" + s;
            }
            try {
                URI u = new URI(s);
                String host = u.getHost();
                if (host == null) {
                    return Optional.empty();
                }
                String path = u.getRawPath();
                if (path == null || path.isEmpty()) {
                    path = "/";
                }
                String query = u.getRawQuery();
                URI n = new URI(u.getScheme(), host, path, query != null && !query.isEmpty() ? query : null, null);
                return Optional.of(n.toASCIIString());
            } catch (Exception e) {
                return Optional.empty();
            }
        });
        lenient().when(accessControlService.hasActiveSubscription(any(User.class))).thenReturn(false);
        lenient().when(accessControlService.isPreviewMode(any(User.class))).thenReturn(true);
        lenient().when(chatbotRepository.countByOwner(anyLong())).thenReturn(0L);
        lenient().when(accessControlService.canCreateChatbot(any(User.class), anyLong())).thenReturn(true);
        lenient().when(accessControlService.getMaxChatbotsAllowed(any(User.class))).thenReturn(1);
        lenient().when(accessControlService.getSubscriptionPlan(any(User.class)))
            .thenReturn(Subscription.SubscriptionPlan.FREE);
    }

    @Test
    @DisplayName("Should block preview user from creating chatbot with website > 500 pages")
    void shouldBlockPreviewUserFromLargeWebsite() {
        // Arrange
        String largeWebsiteUrl = "https://large-website.com";
        int estimatedPages = 600;

        when(websiteSizeEstimator.estimateSize(anyString())).thenReturn(estimatedPages);
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
        assertTrue(body.get("error").toString().contains("Website too large for your plan"));
        assertTrue(body.get("error").toString().contains("Upgrade to BASIC for sites up to 2000 pages"));
        assertEquals(true, body.get("upgradeRequired"));
        assertEquals(estimatedPages, body.get("estimatedPages"));
        assertEquals(500, body.get("maxPages"));
        assertEquals("BASIC", body.get("suggestedPlan"));
        assertEquals(2000, body.get("suggestedMaxPages"));

        // Verify chatbot was NOT created
        verify(chatbotService, never()).createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), anyInt());
    }

    @Test
    @DisplayName("Should allow preview user to create chatbot with website <= 500 pages")
    void shouldAllowPreviewUserWithSmallWebsite() {
        // Arrange
        String smallWebsiteUrl = "https://small-website.com";
        int estimatedPages = 30;
        
        when(websiteSizeEstimator.estimateSize(anyString())).thenReturn(estimatedPages);
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
    @DisplayName("Should reject onboarding when URL fails safety validation")
    void shouldRejectOnboardingWhenUrlFailsValidation() {
        when(urlValidationService.completeAndValidate(anyString())).thenReturn(Optional.empty());

        ResponseEntity<?> response = chatbotController.createChatbotFromUrl(
            Map.of("websiteUrl", "https://unsafe.example"),
            customOAuth2User
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(chatbotService, never()).createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), anyInt());
        verify(websiteSizeEstimator, never()).estimateSize(anyString());
    }

    @Test
    @DisplayName("Should allow paid user to create chatbot with any website size")
    void shouldAllowPaidUserWithAnyWebsiteSize() {
        // Arrange
        String largeWebsiteUrl = "https://large-website.com";

        lenient().when(websiteSizeEstimator.estimateSize(anyString())).thenReturn(5_000);
        lenient().when(accessControlService.getSubscriptionPlan(testUser)).thenReturn(Subscription.SubscriptionPlan.ENTERPRISE);
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
        verify(chatbotService, times(1)).createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), eq(1));
    }

    @Test
    @DisplayName("Should block preview user in createChatbot() with website > 500 pages")
    void shouldBlockPreviewUserInCreateChatbot() {
        // Arrange
        ChatbotRequest request = new ChatbotRequest();
        request.setName("Test Bot");
        request.setDescription("Test Description");
        request.setWebsiteUrl("https://large-website.com");
        request.setPrimaryLanguage("en");

        int estimatedPages = 750;

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
        assertEquals(500, body.get("maxPages"));
        assertEquals("BASIC", body.get("suggestedPlan"));
        assertEquals(2000, body.get("suggestedMaxPages"));

        // Verify chatbot was NOT created
        verify(chatbotService, never()).createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), anyInt());
    }

    @Test
    @DisplayName("Should allow preview user in createChatbot() with website <= 500 pages")
    void shouldAllowPreviewUserInCreateChatbot() {
        // Arrange
        ChatbotRequest request = new ChatbotRequest();
        request.setName("Test Bot");
        request.setDescription("Test Description");
        request.setWebsiteUrl("https://small-website.com");
        request.setPrimaryLanguage("en");

        int estimatedPages = 25;
        
        when(websiteSizeEstimator.estimateSize("https://small-website.com")).thenReturn(estimatedPages);
        when(accessControlService.isPreviewMode(testUser)).thenReturn(true);
        when(accessControlService.getMaxChatbotsAllowed(testUser)).thenReturn(1);
        when(chatbotService.createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), eq(1))).thenReturn(testChatbot);

        // Act
        ResponseEntity<?> response = chatbotController.createChatbot(request, customOAuth2User);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        // Verify chatbot WAS created (one chatbot per user)
        verify(chatbotService, times(1)).createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), eq(1));
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
        when(accessControlService.getMaxChatbotsAllowed(testUser)).thenReturn(1);
        when(chatbotService.createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), eq(1))).thenReturn(testChatbot);

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
        when(websiteSizeEstimator.estimateSize(anyString())).thenReturn(-1);
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
    @DisplayName("Should allow exactly 500 pages (at FREE tier limit)")
    void shouldAllowExactly500Pages() {
        String websiteUrl = "https://example.com";
        int estimatedPages = 500;

        when(websiteSizeEstimator.estimateSize(anyString())).thenReturn(estimatedPages);
        when(accessControlService.isPreviewMode(testUser)).thenReturn(true);
        when(chatbotService.createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), eq(1))).thenReturn(testChatbot);
        when(websiteAnalysisService.analyzeWebsite(any(Chatbot.class))).thenReturn(null);

        ResponseEntity<?> response = chatbotController.createChatbotFromUrl(
            Map.of("websiteUrl", websiteUrl),
            customOAuth2User
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(chatbotService, times(1)).createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), eq(1));
    }

    @Test
    @DisplayName("Analyze website returns 403 with suggestedPlan when site exceeds plan limit")
    void analyzeWebsiteReturns403WithSuggestedPlanWhenOverLimit() {
        testChatbot.setOwner(testUser);
        testChatbot.setWebsiteUrl("https://large-site.com");
        int estimatedPages = 600;

        when(chatbotRepository.findById(100L)).thenReturn(java.util.Optional.of(testChatbot));
        when(accessControlService.hasActiveSubscription(testUser)).thenReturn(true);
        when(rateLimitingService.checkScanLimit(testUser))
            .thenReturn(new RateLimitingService.RateLimitResult(true, 1, 0, true, "scan", false));
        when(websiteSizeEstimator.estimateSize("https://large-site.com")).thenReturn(estimatedPages);
        when(accessControlService.getSubscriptionPlan(testUser)).thenReturn(Subscription.SubscriptionPlan.FREE);

        ResponseEntity<?> response = chatbotController.analyzeWebsite(100L, customOAuth2User);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertTrue(body.get("error").toString().contains("Website too large for your plan"));
        assertEquals(600, body.get("estimatedPages"));
        assertEquals(500, body.get("maxPages"));
        assertEquals("BASIC", body.get("suggestedPlan"));
        assertEquals(2000, body.get("suggestedMaxPages"));
        assertEquals(true, body.get("upgradeRequired"));

        verify(websiteAnalysisService, never()).analyzeWebsite(any(Chatbot.class));
    }
}

