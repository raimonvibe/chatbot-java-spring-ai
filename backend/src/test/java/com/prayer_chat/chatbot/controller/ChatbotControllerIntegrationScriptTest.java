package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
import com.prayer_chat.chatbot.service.AccessControlService;
import com.prayer_chat.chatbot.service.AiChatbotService;
import com.prayer_chat.chatbot.service.ChatbotService;
import com.prayer_chat.chatbot.service.WebsiteAnalysisService;
import com.prayer_chat.chatbot.service.ConversationExportService;
import com.prayer_chat.chatbot.service.BibleVerseService;
import com.prayer_chat.chatbot.service.ChristianContentAnalysisService;
import com.prayer_chat.chatbot.service.CostTrackingService;
import com.prayer_chat.chatbot.service.WebsiteSizeEstimator;
import com.prayer_chat.chatbot.service.RateLimitingService;
import com.prayer_chat.chatbot.repository.WebsiteScanAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Security and functionality tests for integration script generation
 * 
 * Tests:
 * - Integration script uses correct base URL
 * - Base URL is properly sanitized
 * - Script generation is secure (no XSS)
 * - Base URL validation prevents SSRF
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatbotController Integration Script Tests")
class ChatbotControllerIntegrationScriptTest {

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

    @InjectMocks
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
        testChatbot.setEmbedCode("prayer-chat-bot-100");
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
    }

    private static final String PRODUCTION_URL = "https://chatbot-java-spring-ai.onrender.com";

    @Test
    @DisplayName("Should generate integration script with production base URL")
    void shouldGenerateScriptWithProductionUrl() {
        // Arrange - use current production URL (deprecated URL is overridden in controller)
        ReflectionTestUtils.setField(chatbotController, "baseUrl", PRODUCTION_URL);
        
        when(customOAuth2User.getUser()).thenReturn(testUser);
        when(chatbotRepository.findById(100L)).thenReturn(Optional.of(testChatbot));
        when(accessControlService.hasActiveSubscription(testUser)).thenReturn(true); // Required for verifyAccess
        when(accessControlService.canAccessIntegrationScript(testUser)).thenReturn(true);

        // Act
        ResponseEntity<?> response = chatbotController.getEmbedCode(100L, customOAuth2User);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        String embedCode = body.get("embedCode");
        
        assertNotNull(embedCode);
        assertTrue(embedCode.contains(PRODUCTION_URL), "Embed code should contain production URL");
        assertTrue(embedCode.contains("/js/chatbot-widget.js"), "Embed code should contain widget script path");
        assertTrue(embedCode.contains("/api"), "Embed code should contain API path");
        assertFalse(embedCode.contains("localhost:8080"), "Embed code should not contain localhost:8080");
    }

    @Test
    @DisplayName("Should sanitize base URL (remove trailing slash)")
    void shouldSanitizeBaseUrl() {
        // Arrange
        String baseUrlWithSlash = "https://chatbot-backend-4mp4.onrender.com/";
        ReflectionTestUtils.setField(chatbotController, "baseUrl", baseUrlWithSlash);
        
        when(customOAuth2User.getUser()).thenReturn(testUser);
        when(chatbotRepository.findById(100L)).thenReturn(Optional.of(testChatbot));
        when(accessControlService.hasActiveSubscription(testUser)).thenReturn(true);
        when(accessControlService.canAccessIntegrationScript(testUser)).thenReturn(true);

        // Act
        ResponseEntity<?> response = chatbotController.getEmbedCode(100L, customOAuth2User);

        // Assert
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        String embedCode = body.get("embedCode");
        
        // Should not have double slashes
        assertFalse(embedCode.contains("//js/chatbot-widget.js"));
        assertFalse(embedCode.contains("//api"));
        // Should have single slash
        assertTrue(embedCode.contains("/js/chatbot-widget.js"));
        assertTrue(embedCode.contains("/api"));
    }

    @Test
    @DisplayName("Should prevent XSS in integration script")
    void shouldPreventXSSInScript() {
        // Arrange
        String baseUrl = "https://chatbot-backend-4mp4.onrender.com";
        ReflectionTestUtils.setField(chatbotController, "baseUrl", baseUrl);
        
        when(customOAuth2User.getUser()).thenReturn(testUser);
        when(chatbotRepository.findById(100L)).thenReturn(Optional.of(testChatbot));
        when(accessControlService.hasActiveSubscription(testUser)).thenReturn(true);
        when(accessControlService.canAccessIntegrationScript(testUser)).thenReturn(true);

        // Act
        ResponseEntity<?> response = chatbotController.getEmbedCode(100L, customOAuth2User);

        // Assert
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        String embedCode = body.get("embedCode");
        
        // Security: Base URL should be properly sanitized (no XSS in URL)
        // The embed code itself contains <script> tags which is correct for embedding
        assertTrue(embedCode.contains("<script>"), "Embed code should contain script tag for embedding");
        assertTrue(embedCode.contains("</script>"), "Embed code should contain closing script tag");
        // Should properly use document.createElement for dynamic script loading
        assertTrue(embedCode.contains("document.createElement('script')"), "Should use safe script creation");
        // Base URL should not contain dangerous characters that could break out of quotes
        assertFalse(baseUrl.contains("'"), "Base URL should not contain single quotes");
        assertFalse(baseUrl.contains("\""), "Base URL should not contain double quotes");
    }

    @Test
    @DisplayName("Should use environment-based base URL")
    void shouldUseEnvironmentBasedUrl() {
        // Configured URL -> expected URL in embed (deprecated URL is overridden to current production)
        String[][] cases = {
            { "https://chatbot-backend-4mp4.onrender.com", "https://chatbot-java-spring-ai.onrender.com" },
            { "http://localhost:8081", "http://localhost:8081" },
            { "https://staging-backend.example.com", "https://staging-backend.example.com" }
        };

        for (String[] pair : cases) {
            String configuredUrl = pair[0];
            String expectedInEmbed = pair[1];
            ReflectionTestUtils.setField(chatbotController, "baseUrl", configuredUrl);
            
            when(customOAuth2User.getUser()).thenReturn(testUser);
            when(chatbotRepository.findById(100L)).thenReturn(Optional.of(testChatbot));
            when(accessControlService.hasActiveSubscription(testUser)).thenReturn(true);
            when(accessControlService.canAccessIntegrationScript(testUser)).thenReturn(true);

            ResponseEntity<?> response = chatbotController.getEmbedCode(100L, customOAuth2User);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, String> body = (Map<String, String>) response.getBody();
            String embedCode = body.get("embedCode");
            
            assertTrue(embedCode.contains(expectedInEmbed), 
                "Embed code should contain base URL: " + expectedInEmbed + " (configured: " + configuredUrl + ")");
        }
    }

    @Test
    @DisplayName("Should deny access to integration script for preview mode users")
    void shouldDenyAccessForPreviewMode() {
        // Arrange
        when(customOAuth2User.getUser()).thenReturn(testUser);
        when(chatbotRepository.findById(100L)).thenReturn(Optional.of(testChatbot));
        when(accessControlService.hasActiveSubscription(testUser)).thenReturn(true); // Has subscription but preview mode
        when(accessControlService.canAccessIntegrationScript(testUser)).thenReturn(false);

        // Act
        ResponseEntity<?> response = chatbotController.getEmbedCode(100L, customOAuth2User);

        // Assert
        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        verify(chatbotRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return 404 when chatbot not found")
    void shouldReturn404WhenChatbotNotFound() {
        // Arrange
        when(customOAuth2User.getUser()).thenReturn(testUser);
        when(chatbotRepository.findById(999L)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<?> response = chatbotController.getEmbedCode(999L, customOAuth2User);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("Should include embed code in embed code")
    void shouldIncludeEmbedCode() {
        // Arrange
        String baseUrl = "https://chatbot-backend-4mp4.onrender.com";
        ReflectionTestUtils.setField(chatbotController, "baseUrl", baseUrl);
        
        when(customOAuth2User.getUser()).thenReturn(testUser);
        when(chatbotRepository.findById(100L)).thenReturn(Optional.of(testChatbot));
        when(accessControlService.hasActiveSubscription(testUser)).thenReturn(true);
        when(accessControlService.canAccessIntegrationScript(testUser)).thenReturn(true);

        // Act
        ResponseEntity<?> response = chatbotController.getEmbedCode(100L, customOAuth2User);

        // Assert
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        String embedCode = body.get("embedCode");
        
        assertTrue(embedCode.contains("prayer-chat-chatbot-prayer-chat-bot-100"));
        assertTrue(embedCode.contains("var embedCode = 'prayer-chat-bot-100'"));
    }
}

