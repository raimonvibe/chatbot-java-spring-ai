package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.repository.WebsiteScanAuditRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Security tests for URL handling in ChatbotController
 * 
 * Verifies that integration script URLs are generated securely
 * and prevent SSRF, XSS, and other security vulnerabilities.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatbotController URL Security Tests")
class ChatbotControllerUrlSecurityTest {

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
        // Mock access control - user has access to integration script
        lenient().when(accessControlService.hasActiveSubscription(any(User.class))).thenReturn(true);
        lenient().when(accessControlService.isPreviewMode(any(User.class))).thenReturn(false);
        lenient().when(accessControlService.canAccessIntegrationScript(any(User.class))).thenReturn(true);
        
        // Mock chatbot repository for all tests
        lenient().when(chatbotRepository.findById(100L)).thenReturn(Optional.of(testChatbot));
    }

    @Test
    @DisplayName("Should sanitize baseUrl to prevent XSS in embed code")
    void shouldSanitizeBaseUrlToPreventXSS() {
        // Arrange - malicious baseUrl with script injection attempt
        String maliciousBaseUrl = "https://example.com'</script><script>alert('XSS')</script>";
        ReflectionTestUtils.setField(chatbotController, "baseUrl", maliciousBaseUrl);

        // Act
        ResponseEntity<?> response = chatbotController.getEmbedCode(100L, customOAuth2User);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        String embedCode = (String) body.get("embedCode");
        
        // Invalid/malicious baseUrl is replaced with default; output must not contain the malicious fragment
        assertNotNull(embedCode);
        assertFalse(embedCode.contains("</script><script>alert('XSS')</script>"));
        // Safe default URL is used when configured baseUrl is invalid
        assertTrue(embedCode.contains("chatbot-backend") || embedCode.contains("render.com"));
    }

    @Test
    @DisplayName("Should remove trailing slash from baseUrl")
    void shouldRemoveTrailingSlashFromBaseUrl() {
        // Arrange
        String baseUrlWithSlash = "https://chatbot-backend-4mp4.onrender.com/";
        ReflectionTestUtils.setField(chatbotController, "baseUrl", baseUrlWithSlash);
        

        // Act
        ResponseEntity<?> response = chatbotController.getEmbedCode(100L, customOAuth2User);

        // Assert
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        String embedCode = (String) body.get("embedCode");
        
        // Should not have double slashes
        assertFalse(embedCode.contains("//js/chatbot-widget.js"));
        assertFalse(embedCode.contains("//api"));
        // Should have single slash
        assertTrue(embedCode.contains("/js/chatbot-widget.js"));
        assertTrue(embedCode.contains("/api"));
    }

    @Test
    @DisplayName("Should use production URL from configuration")
    void shouldUseProductionUrlFromConfiguration() {
        // Arrange
        String productionUrl = "https://chatbot-backend-4mp4.onrender.com";
        ReflectionTestUtils.setField(chatbotController, "baseUrl", productionUrl);
        

        // Act
        ResponseEntity<?> response = chatbotController.getEmbedCode(100L, customOAuth2User);

        // Assert
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        String embedCode = (String) body.get("embedCode");
        
        assertTrue(embedCode.contains(productionUrl));
        assertFalse(embedCode.contains("localhost"));
    }

    @Test
    @DisplayName("Should reject javascript: URLs in baseUrl and use default")
    void shouldSanitizeJavascriptUrls() {
        // Arrange - invalid baseUrl is replaced with default (EmbedSecurity.validateAndNormalizeBaseUrl)
        String javascriptUrl = "javascript:alert('XSS')";
        ReflectionTestUtils.setField(chatbotController, "baseUrl", javascriptUrl);

        // Act
        ResponseEntity<?> response = chatbotController.getEmbedCode(100L, customOAuth2User);

        // Assert - embed code must not contain javascript: protocol; must use safe default URL
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        String embedCode = (String) body.get("embedCode");
        assertNotNull(embedCode);
        assertFalse(embedCode.contains("javascript:"));
        assertTrue(embedCode.contains("chatbot-backend") || embedCode.contains("render.com"));
    }

    @Test
    @DisplayName("Should reject baseUrl with special characters and use default")
    void shouldEscapeSpecialCharactersInBaseUrl() {
        // Arrange - baseUrl with quotes fails validation and is replaced with default
        String baseUrlWithSpecialChars = "https://example.com/path'with\"quotes";
        ReflectionTestUtils.setField(chatbotController, "baseUrl", baseUrlWithSpecialChars);

        // Act
        ResponseEntity<?> response = chatbotController.getEmbedCode(100L, customOAuth2User);

        // Assert - output must not contain unescaped malicious baseUrl; must use safe default
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        String embedCode = (String) body.get("embedCode");
        assertNotNull(embedCode);
        assertFalse(embedCode.contains("path'with"));
        assertTrue(embedCode.contains("chatbot-backend") || embedCode.contains("render.com"));
    }

    @Test
    @DisplayName("Should include chatbot ID in embed code")
    void shouldIncludeChatbotIdInEmbedCode() {
        // Arrange
        String baseUrl = "https://chatbot-backend-4mp4.onrender.com";
        ReflectionTestUtils.setField(chatbotController, "baseUrl", baseUrl);
        

        // Act
        ResponseEntity<?> response = chatbotController.getEmbedCode(100L, customOAuth2User);

        // Assert
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        String embedCode = (String) body.get("embedCode");
        
        assertTrue(embedCode.contains("prayer-chat-chatbot-100"));
        assertTrue(embedCode.contains("chatbotId: 100"));
    }

    @Test
    @DisplayName("Should use HTTPS URLs in production")
    void shouldUseHttpsUrlsInProduction() {
        // Arrange
        String httpsUrl = "https://chatbot-backend-4mp4.onrender.com";
        ReflectionTestUtils.setField(chatbotController, "baseUrl", httpsUrl);
        

        // Act
        ResponseEntity<?> response = chatbotController.getEmbedCode(100L, customOAuth2User);

        // Assert
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        String embedCode = (String) body.get("embedCode");
        
        assertTrue(embedCode.contains("https://"));
        assertFalse(embedCode.contains("http://") && !embedCode.contains("https://"));
    }

    @Test
    @DisplayName("Should not expose sensitive information in embed code")
    void shouldNotExposeSensitiveInformation() {
        // Arrange
        String baseUrl = "https://chatbot-backend-4mp4.onrender.com";
        ReflectionTestUtils.setField(chatbotController, "baseUrl", baseUrl);
        

        // Act
        ResponseEntity<?> response = chatbotController.getEmbedCode(100L, customOAuth2User);

        // Assert
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        String embedCode = (String) body.get("embedCode");
        
        // Should not contain API keys, secrets, or tokens
        assertFalse(embedCode.contains("api_key"));
        assertFalse(embedCode.contains("secret"));
        assertFalse(embedCode.contains("token"));
        assertFalse(embedCode.contains("password"));
    }
}

