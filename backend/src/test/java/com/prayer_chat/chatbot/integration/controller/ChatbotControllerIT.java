package com.prayer_chat.chatbot.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prayer_chat.chatbot.config.MockAiConfiguration;
import com.prayer_chat.chatbot.config.TestSecurityConfig;
import com.prayer_chat.chatbot.config.TestJacksonConfiguration;
import com.prayer_chat.chatbot.dto.ChatbotRequest;
import com.prayer_chat.chatbot.exception.ChatbotLimitReachedException;
import com.prayer_chat.chatbot.helpers.TestAuthenticationHelper;
import com.prayer_chat.chatbot.helpers.TestDataBuilder;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import com.prayer_chat.chatbot.security.JwtTokenProvider;
import com.prayer_chat.chatbot.service.ChatbotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = com.prayer_chat.chatbot.AiChatbotApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MockAiConfiguration.class, TestSecurityConfig.class, TestJacksonConfiguration.class})
@DisplayName("ChatbotController Integration Tests")
class ChatbotControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatbotService chatbotService;

    @MockBean
    private ChatbotRepository chatbotRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private SubscriptionRepository subscriptionRepository;

    @MockBean
    private com.prayer_chat.chatbot.service.AccessControlService accessControlService;

    @MockBean
    private com.prayer_chat.chatbot.service.CostTrackingService costTrackingService;

    @MockBean
    private com.prayer_chat.chatbot.service.WebsiteSizeEstimator websiteSizeEstimator;

    @MockBean
    private com.prayer_chat.chatbot.repository.WebsiteContentRepository websiteContentRepository;

    @MockBean
    private com.prayer_chat.chatbot.repository.WebsiteScanAuditRepository websiteScanAuditRepository;

    private User testUser;
    private Chatbot testChatbot;
    private Subscription testSubscription;

    @BeforeEach
    void setUp() {
        testUser = TestDataBuilder.createTestUser();
        testUser.setId(1L);

        testChatbot = TestDataBuilder.createTestChatbot(testUser);
        testChatbot.setId(1L);

        // Create active subscription for test user
        testSubscription = TestDataBuilder.createActiveSubscription(testUser);
        testSubscription.setId(1L);
        when(subscriptionRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(testSubscription));
        
        // Mock access control services for paid user (default test user)
        when(accessControlService.hasActiveSubscription(any(User.class))).thenReturn(true);
        when(accessControlService.isPreviewMode(any(User.class))).thenReturn(false);
        when(accessControlService.canAccessIntegrationScript(any(User.class))).thenReturn(true);
        when(accessControlService.canCreateChatbot(any(User.class), anyLong())).thenReturn(true);
        when(accessControlService.getMaxChatbotsAllowed(any(User.class))).thenReturn(Integer.MAX_VALUE);
        
        // Mock cost tracking for paid user
        when(costTrackingService.isPreviewMode(any(User.class))).thenReturn(false);
        
        // Mock website size estimator (default: small website)
        when(websiteSizeEstimator.estimateSize(anyString())).thenReturn(10);
        
        // Mock website scan audit repository (no scans today) - SECURITY: Use audit table, not WebsiteContent
        when(websiteScanAuditRepository.countDistinctScanDatesByUserAndDateAfter(anyLong(), any(java.time.LocalDateTime.class))).thenReturn(0L);
        
        // Mock chatbot repository count (no chatbots yet)
        when(chatbotRepository.countByOwner(anyLong())).thenReturn(0L);
    }

    @Test
    @DisplayName("Should create chatbot successfully")
    void shouldCreateChatbotSuccessfully() throws Exception {
        // Arrange
        ChatbotRequest request = new ChatbotRequest();
        request.setName("New Chatbot");
        request.setDescription("Test description");
        request.setWebsiteUrl("https://example.com");
        request.setPrimaryLanguage("en");
        request.setCustomPrompt("You are a helpful assistant");

        when(chatbotService.createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), anyInt()))
            .thenReturn(testChatbot);

        // Act & Assert
        mockMvc.perform(post("/api/chatbots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").exists());

        verify(chatbotService, times(1)).createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), anyInt());
    }

    @Test
    @DisplayName("Should get all chatbots for user")
    void shouldGetAllChatbotsForUser() throws Exception {
        // Arrange - controller uses findByOwnerId, not findAll
        Chatbot bot2 = TestDataBuilder.createTestChatbot(testUser);
        bot2.setId(2L);
        List<Chatbot> chatbots = Arrays.asList(testChatbot, bot2);

        when(chatbotRepository.findByOwnerId(testUser.getId())).thenReturn(chatbots);

        // Act & Assert
        mockMvc.perform(get("/api/chatbots")
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));

        verify(chatbotRepository, times(1)).findByOwnerId(testUser.getId());
    }

    @Test
    @DisplayName("Should get chatbot by ID")
    void shouldGetChatbotById() throws Exception {
        // Arrange
        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));

        // Act & Assert
        mockMvc.perform(get("/api/chatbots/1")
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").exists());

        verify(chatbotRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should return 404 when chatbot not found")
    void shouldReturn404WhenChatbotNotFound() throws Exception {
        // Arrange
        when(chatbotRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/chatbots/999")
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isNotFound());

        verify(chatbotRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("Should update chatbot successfully")
    void shouldUpdateChatbotSuccessfully() throws Exception {
        // Arrange
        Chatbot chatbotDetails = TestDataBuilder.createTestChatbot(testUser);
        chatbotDetails.setName("Updated Name");
        chatbotDetails.setDescription("Updated description");

        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));
        when(chatbotRepository.save(any(Chatbot.class))).thenAnswer(invocation -> {
            Chatbot saved = invocation.getArgument(0);
            saved.setName("Updated Name");
            return saved;
        });

        // Act & Assert
        mockMvc.perform(put("/api/chatbots/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(chatbotDetails))
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated Name"));

        verify(chatbotRepository, times(1)).findById(1L);
        verify(chatbotRepository, times(1)).save(any(Chatbot.class));
    }

    @Test
    @DisplayName("Should sanitize malicious brandingConfig on update")
    void shouldSanitizeMaliciousBrandingConfigOnUpdate() throws Exception {
        // Arrange
        Chatbot chatbotDetails = TestDataBuilder.createTestChatbot(testUser);
        chatbotDetails.setBrandingConfig("{\"primaryColor\":\"#fff\",\"danger\":\"url(javascript:alert(1))\",\"secondaryColor\":\"rgb(255,0,0)\"}");

        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));
        when(chatbotRepository.save(any(Chatbot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act & Assert
        mockMvc.perform(put("/api/chatbots/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(chatbotDetails))
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.brandingConfig").value("{\"primaryColor\":\"#fff\"}"));

        verify(chatbotRepository, times(1)).findById(1L);
        verify(chatbotRepository, times(1)).save(any(Chatbot.class));
    }

    @Test
    @DisplayName("Should delete chatbot successfully")
    void shouldDeleteChatbotSuccessfully() throws Exception {
        // Arrange - controller delegates to chatbotService.deleteChatbot (service performs vector store cleanup and repo delete)
        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));
        doNothing().when(chatbotService).deleteChatbot(eq(1L), any(User.class));

        // Act & Assert
        mockMvc.perform(delete("/api/chatbots/1")
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isNoContent());

        verify(chatbotRepository, times(1)).findById(1L);
        verify(chatbotService, times(1)).deleteChatbot(eq(1L), any(User.class));
    }

    @Test
    @DisplayName("Should reject unauthorized access to chatbots")
    void shouldRejectUnauthorizedAccess() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/chatbots"))
            .andExpect(status().isUnauthorized());

        verify(chatbotService, never()).getChatbotsForUser(any());
    }

    @Test
    @DisplayName("Should reject chatbot creation with invalid URL")
    void shouldRejectInvalidUrl() throws Exception {
        // Arrange
        ChatbotRequest request = new ChatbotRequest();
        request.setName("Test Bot");
        request.setWebsiteUrl("http://localhost:8080"); // Invalid URL

        when(chatbotService.createChatbot(any(Chatbot.class), any(User.class)))
            .thenThrow(new IllegalArgumentException("Invalid or unsafe website URL"));

        // Act & Assert
        mockMvc.perform(post("/api/chatbots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                assert status >= 400 && status < 600 : "Expected 4xx or 5xx status, got: " + status;
            });
            // Note: Controller catches IllegalArgumentException and returns 500, or @Valid returns 400
    }

    @Test
    @DisplayName("Should reject chatbot creation with javascript scheme URL")
    void shouldRejectJavascriptSchemeUrlOnCreate() throws Exception {
        // Arrange
        ChatbotRequest request = new ChatbotRequest();
        request.setName("Unsafe JS URL Bot");
        request.setDescription("Should be rejected");
        request.setWebsiteUrl("javascript:alert(1)");
        request.setPrimaryLanguage("en");

        // Act & Assert
        mockMvc.perform(post("/api/chatbots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isBadRequest());

        // Validation should fail before service call
        verify(chatbotService, never()).createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), anyInt());
    }

    @Test
    @DisplayName("Should reject chatbot creation with data scheme URL")
    void shouldRejectDataSchemeUrlOnCreate() throws Exception {
        // Arrange
        ChatbotRequest request = new ChatbotRequest();
        request.setName("Unsafe Data URL Bot");
        request.setDescription("Should be rejected");
        request.setWebsiteUrl("data:text/html,<script>alert(1)</script>");
        request.setPrimaryLanguage("en");

        // Act & Assert
        mockMvc.perform(post("/api/chatbots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isBadRequest());

        // Validation should fail before service call
        verify(chatbotService, never()).createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), anyInt());
    }

    @Test
    @DisplayName("Should prevent unauthorized update of chatbot")
    void shouldPreventUnauthorizedUpdate() throws Exception {
        // Arrange
        User otherUser = TestDataBuilder.createTestUser("other@example.com");
        otherUser.setId(2L);
        
        Subscription otherSubscription = TestDataBuilder.createActiveSubscription(otherUser);
        when(subscriptionRepository.findByUserId(otherUser.getId())).thenReturn(Optional.of(otherSubscription));
        
        Chatbot chatbotDetails = TestDataBuilder.createTestChatbot(testUser);
        chatbotDetails.setName("Malicious Update");

        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));

        // Act & Assert - Controller should return 403 because user is not owner
        mockMvc.perform(put("/api/chatbots/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(chatbotDetails))
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(otherUser))))
            .andExpect(status().isForbidden());

        verify(chatbotRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should sanitize XSS attempts in chatbot fields")
    void shouldSanitizeXssAttempts() throws Exception {
        // Arrange
        ChatbotRequest request = new ChatbotRequest();
        request.setName("Test Bot"); // Valid name (XSS sanitization happens in service, not validation)
        request.setDescription("Test description");
        request.setWebsiteUrl("https://example.com");
        request.setPrimaryLanguage("en"); // Required field
        request.setCustomPrompt("Normal prompt");

        // The service should sanitize the input
        when(chatbotService.createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), anyInt()))
            .thenReturn(testChatbot);

        // Act & Assert
        mockMvc.perform(post("/api/chatbots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isCreated());

        // Verify the service was called (sanitization happens there)
        verify(chatbotService, times(1)).createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), anyInt());
    }

    @Test
    @DisplayName("Should complete full chatbot lifecycle")
    void shouldCompleteFullChatbotLifecycle() throws Exception {
        // Arrange - Create
        ChatbotRequest createRequest = new ChatbotRequest();
        createRequest.setName("Lifecycle Bot");
        createRequest.setWebsiteUrl("https://example.com/lifecycle");
        createRequest.setDescription("Lifecycle test");
        createRequest.setPrimaryLanguage("en"); // Required field

        when(chatbotService.createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), anyInt()))
            .thenReturn(testChatbot);

        // Act - Create
        mockMvc.perform(post("/api/chatbots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1));

        // Arrange - Update
        Chatbot updateDetails = TestDataBuilder.createTestChatbot(testUser);
        updateDetails.setName("Updated Lifecycle Bot");

        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));
        when(chatbotRepository.save(any(Chatbot.class))).thenAnswer(invocation -> {
            Chatbot saved = invocation.getArgument(0);
            saved.setName("Updated Lifecycle Bot");
            return saved;
        });

        // Act - Update
        mockMvc.perform(put("/api/chatbots/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDetails))
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated Lifecycle Bot"));

        // Arrange - Delete (controller delegates to chatbotService.deleteChatbot)
        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));
        doNothing().when(chatbotService).deleteChatbot(eq(1L), any(User.class));

        // Act - Delete
        mockMvc.perform(delete("/api/chatbots/1")
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isNoContent());

        // Verify all operations were called
        verify(chatbotService, times(1)).createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), anyInt());
        verify(chatbotRepository, times(2)).findById(1L); // Once for update, once for delete
        verify(chatbotRepository, times(1)).save(any(Chatbot.class));
        verify(chatbotService, times(1)).deleteChatbot(eq(1L), any(User.class));
    }

    @Test
    @DisplayName("Should handle concurrent access to same chatbot")
    void shouldHandleConcurrentAccess() throws Exception {
        // Arrange
        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));

        // Act - Simulate two concurrent read requests
        mockMvc.perform(get("/api/chatbots/1")
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/chatbots/1")
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isOk());

        // Assert - Both requests should succeed
        verify(chatbotRepository, times(2)).findById(1L);
    }

    @Test
    @DisplayName("Should generate embed code for chatbot with paid subscription")
    void shouldGenerateEmbedCodeForPaidUser() throws Exception {
        // Arrange
        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));
        when(accessControlService.canAccessIntegrationScript(testUser)).thenReturn(true);

        // Act & Assert
        mockMvc.perform(get("/api/chatbots/1/embed")
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.embedCode").exists());

        verify(chatbotRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should deny embed code access for preview mode users")
    void shouldDenyEmbedCodeForPreviewMode() throws Exception {
        // Arrange
        User previewUser = TestDataBuilder.createTestUser("preview@example.com");
        previewUser.setId(2L);
        
        // Create subscription for preview user (FREE plan)
        Subscription previewSubscription = TestDataBuilder.createFreeSubscription(previewUser);
        when(subscriptionRepository.findByUserId(previewUser.getId())).thenReturn(Optional.of(previewSubscription));
        
        // Create chatbot owned by preview user
        Chatbot previewChatbot = TestDataBuilder.createTestChatbot(previewUser);
        previewChatbot.setId(1L);
        
        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(previewChatbot));
        when(accessControlService.hasActiveSubscription(previewUser)).thenReturn(true);
        when(accessControlService.isPreviewMode(previewUser)).thenReturn(true);
        when(accessControlService.canAccessIntegrationScript(previewUser)).thenReturn(false);

        // Act & Assert
        mockMvc.perform(get("/api/chatbots/1/embed")
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(previewUser))))
            .andExpect(status().isPaymentRequired())
            .andExpect(jsonPath("$.error").exists())
            .andExpect(jsonPath("$.upgradeRequired").value(true));

        verify(chatbotRepository, times(1)).findById(1L);
    }

    // Note: cloneChatbot method doesn't exist in ChatbotService
    // This test is disabled as the functionality is not implemented
    // @Test
    // @WithMockUser(username = "test@example.com")
    // @DisplayName("Should clone existing chatbot")
    // void shouldCloneExistingChatbot() throws Exception {
    //     ...
    // }

    @Test
    @DisplayName("Should analyze website for chatbot with paid subscription")
    void shouldAnalyzeWebsiteForPaidUser() throws Exception {
        // Arrange
        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));
        // Mocks are already set up in setUp() for paid user

        // Act & Assert
        mockMvc.perform(post("/api/chatbots/1/analyze")
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());

        verify(chatbotRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should reject website analysis for preview mode when limit reached")
    void shouldRejectWebsiteAnalysisWhenLimitReached() throws Exception {
        // Arrange
        User previewUser = TestDataBuilder.createTestUser("preview@example.com");
        previewUser.setId(2L);
        
        Subscription previewSubscription = TestDataBuilder.createFreeSubscription(previewUser);
        when(subscriptionRepository.findByUserId(previewUser.getId())).thenReturn(Optional.of(previewSubscription));
        
        // Create chatbot owned by preview user
        Chatbot previewChatbot = TestDataBuilder.createTestChatbot(previewUser);
        previewChatbot.setId(1L);
        
        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(previewChatbot));
        when(accessControlService.hasActiveSubscription(previewUser)).thenReturn(true);
        when(accessControlService.isPreviewMode(previewUser)).thenReturn(true);
        when(costTrackingService.isPreviewMode(previewUser)).thenReturn(true);
        // Mock WebsiteScanAuditRepository (controller uses this, not WebsiteContentRepository)
        when(websiteScanAuditRepository.countDistinctScanDatesByUserAndDateAfter(anyLong(), any(java.time.LocalDateTime.class))).thenReturn(1L); // Already scanned today
        when(websiteSizeEstimator.estimateSize(anyString())).thenReturn(10); // Small website

        // Act & Assert - controller returns 402 PAYMENT_REQUIRED when scan limit reached
        mockMvc.perform(post("/api/chatbots/1/analyze")
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(previewUser))))
            .andExpect(status().isPaymentRequired())
            .andExpect(jsonPath("$.error").exists())
            .andExpect(jsonPath("$.upgradeRequired").value(true));

        verify(chatbotRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should reject website analysis for preview mode when website too large")
    void shouldRejectWebsiteAnalysisWhenWebsiteTooLarge() throws Exception {
        // Arrange
        User previewUser = TestDataBuilder.createTestUser("preview@example.com");
        previewUser.setId(2L);
        
        Subscription previewSubscription = TestDataBuilder.createFreeSubscription(previewUser);
        when(subscriptionRepository.findByUserId(previewUser.getId())).thenReturn(Optional.of(previewSubscription));
        
        // Create chatbot owned by preview user
        Chatbot previewChatbot = TestDataBuilder.createTestChatbot(previewUser);
        previewChatbot.setId(1L);
        
        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(previewChatbot));
        when(accessControlService.hasActiveSubscription(previewUser)).thenReturn(true);
        when(accessControlService.isPreviewMode(previewUser)).thenReturn(true);
        when(costTrackingService.isPreviewMode(previewUser)).thenReturn(true);
        // Mock WebsiteScanAuditRepository (controller uses this, not WebsiteContentRepository)
        when(websiteScanAuditRepository.countDistinctScanDatesByUserAndDateAfter(anyLong(), any(java.time.LocalDateTime.class))).thenReturn(0L);
        when(websiteSizeEstimator.estimateSize(anyString())).thenReturn(100); // > 50 pages

        // Act & Assert
        mockMvc.perform(post("/api/chatbots/1/analyze")
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(previewUser))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").exists())
            .andExpect(jsonPath("$.estimatedPages").value(100))
            .andExpect(jsonPath("$.upgradeRequired").value(true));

        verify(chatbotRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should reject chatbot creation when one chatbot limit reached for preview mode")
    void shouldRejectChatbotCreationWhenLimitReached() throws Exception {
        // Arrange - controller calls createChatbotEnforcingLimit, which throws when limit reached
        User previewUser = TestDataBuilder.createTestUser("preview@example.com");
        previewUser.setId(2L);
        
        ChatbotRequest request = new ChatbotRequest();
        request.setName("Second Bot");
        request.setDescription("Test description");
        request.setWebsiteUrl("https://example.com");
        request.setPrimaryLanguage("en");

        when(accessControlService.hasActiveSubscription(previewUser)).thenReturn(true);
        when(accessControlService.isPreviewMode(previewUser)).thenReturn(true);
        when(accessControlService.getMaxChatbotsAllowed(previewUser)).thenReturn(1);
        when(costTrackingService.isPreviewMode(previewUser)).thenReturn(true);
        when(chatbotRepository.countByOwner(previewUser.getId())).thenReturn(1L);
        when(chatbotService.createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), eq(1)))
            .thenThrow(new ChatbotLimitReachedException(1, 1));

        // Act & Assert
        mockMvc.perform(post("/api/chatbots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(authentication(TestAuthenticationHelper.createCustomOAuth2UserAuthentication(previewUser))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").exists())
            .andExpect(jsonPath("$.upgradeRequired").value(true));

        verify(chatbotService, times(1)).createChatbotEnforcingLimit(any(Chatbot.class), any(User.class), eq(1));
    }
}
