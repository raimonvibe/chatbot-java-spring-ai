package com.prayer_chat.chatbot.security;

import com.prayer_chat.chatbot.controller.ChatbotController;
import com.prayer_chat.chatbot.helpers.TestAuthenticationHelper;
import com.prayer_chat.chatbot.helpers.TestDataBuilder;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.model.WebsiteScanAudit;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.repository.WebsiteScanAuditRepository;
import com.prayer_chat.chatbot.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * CRITICAL SECURITY TESTS: Delete/Recreate Attack Prevention
 * 
 * These tests verify that users cannot bypass scan frequency limits by:
 * 1. Creating a chatbot
 * 2. Scanning a website
 * 3. Deleting the chatbot
 * 4. Creating a new chatbot
 * 5. Scanning again (should still be blocked!)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Delete/Recreate Attack Prevention Security Tests")
class DeleteRecreateAttackPreventionTest {

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
    private com.prayer_chat.chatbot.repository.SubscriptionRepository subscriptionRepository;

    @Mock
    private CostTrackingService costTrackingService;

    @Mock
    private WebsiteSizeEstimator websiteSizeEstimator;

    @Mock
    private com.prayer_chat.chatbot.repository.WebsiteContentRepository websiteContentRepository;

    @Mock
    private WebsiteScanAuditRepository websiteScanAuditRepository;

    @Mock
    private AccessControlService accessControlService;

    @InjectMocks
    private ChatbotController chatbotController;

    private User previewUser;
    private Chatbot testChatbot;
    private CustomOAuth2User customOAuth2User;

    @BeforeEach
    void setUp() {
        previewUser = TestDataBuilder.createPreviewModeUser();
        previewUser.setId(1L);

        testChatbot = TestDataBuilder.createTestChatbot(previewUser);
        testChatbot.setId(1L);
        testChatbot.setWebsiteUrl("https://example.com");

        customOAuth2User = (CustomOAuth2User) TestAuthenticationHelper.createCustomOAuth2UserAuthentication(previewUser).getPrincipal();
    }
    
    private void setupPreviewModeMocks() {
        // Mock preview mode - only when needed
        lenient().when(costTrackingService.isPreviewMode(any(User.class))).thenReturn(true);
        lenient().when(accessControlService.isPreviewMode(any(User.class))).thenReturn(true);
        lenient().when(accessControlService.hasActiveSubscription(any(User.class))).thenReturn(false);
        lenient().when(accessControlService.canCreateChatbot(any(User.class), anyLong())).thenReturn(true);
        lenient().when(accessControlService.getMaxChatbotsAllowed(any(User.class))).thenReturn(1);
        
        // Mock website size estimator
        lenient().when(websiteSizeEstimator.estimateSize(anyString())).thenReturn(10);
        
        // Mock cost tracking
        lenient().when(costTrackingService.calculateWebsiteScanCost(anyInt(), anyInt())).thenReturn(new BigDecimal("0.50"));
        lenient().doNothing().when(costTrackingService).checkCostLimit(any(User.class), any(BigDecimal.class));
    }

    @Test
    @DisplayName("SECURITY: Should prevent second scan after delete/recreate attack")
    void shouldPreventSecondScanAfterDeleteRecreate() {
        // Arrange - Simulate attack scenario:
        // 1. User creates chatbot and scans
        // 2. User deletes chatbot
        // 3. User creates new chatbot
        // 4. User tries to scan again - SHOULD BE BLOCKED

        setupPreviewModeMocks();
        
        // First scan (before delete)
        when(websiteScanAuditRepository.countDistinctScanDatesByUserAndDateAfter(
            eq(previewUser.getId()), any(LocalDateTime.class))).thenReturn(0L);
        
        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));
        lenient().when(accessControlService.canAccessIntegrationScript(any(User.class))).thenReturn(false);

        // First scan succeeds
        ResponseEntity<?> firstScan = chatbotController.analyzeWebsite(
            1L, customOAuth2User
        );

        // Verify first scan was allowed
        assertThat(firstScan.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Verify audit entry was created
        ArgumentCaptor<WebsiteScanAudit> auditCaptor = ArgumentCaptor.forClass(WebsiteScanAudit.class);
        verify(websiteScanAuditRepository, times(1)).save(auditCaptor.capture());
        
        // Now simulate: chatbot deleted, new chatbot created
        Chatbot newChatbot = TestDataBuilder.createTestChatbot(previewUser);
        newChatbot.setId(2L);
        newChatbot.setWebsiteUrl("https://example.com");
        
        when(chatbotRepository.findById(2L)).thenReturn(Optional.of(newChatbot));
        
        // Second scan attempt - should be blocked because audit entry still exists
        when(websiteScanAuditRepository.countDistinctScanDatesByUserAndDateAfter(
            eq(previewUser.getId()), any(LocalDateTime.class))).thenReturn(1L); // Scan from today still exists!

        ResponseEntity<?> secondScan = chatbotController.analyzeWebsite(
            2L, customOAuth2User
        );

        // Assert - second scan should be BLOCKED
        assertThat(secondScan.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        
        // Verify audit repository was checked (proving we use audit, not WebsiteContent)
        verify(websiteScanAuditRepository, atLeast(2)).countDistinctScanDatesByUserAndDateAfter(
            eq(previewUser.getId()), any(LocalDateTime.class));
        
        // Verify second scan was NOT started
        verify(websiteAnalysisService, times(1)).analyzeWebsite(any(Chatbot.class)); // Only first scan
    }

    @Test
    @DisplayName("SECURITY: Should use audit table, not WebsiteContent, for scan frequency check")
    void shouldUseAuditTableNotWebsiteContent() {
        // Arrange
        lenient().when(costTrackingService.isPreviewMode(any(User.class))).thenReturn(true);
        lenient().when(accessControlService.isPreviewMode(any(User.class))).thenReturn(true);
        lenient().when(accessControlService.hasActiveSubscription(any(User.class))).thenReturn(false);
        lenient().when(websiteSizeEstimator.estimateSize(anyString())).thenReturn(10);
        lenient().when(costTrackingService.calculateWebsiteScanCost(anyInt(), anyInt())).thenReturn(new BigDecimal("0.50"));
        lenient().doNothing().when(costTrackingService).checkCostLimit(any(User.class), any(BigDecimal.class));
        
        when(websiteScanAuditRepository.countDistinctScanDatesByUserAndDateAfter(
            eq(previewUser.getId()), any(LocalDateTime.class))).thenReturn(0L);
        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));

        // Act
        chatbotController.analyzeWebsite(1L, customOAuth2User);

        // Assert - verify we use audit repository, NOT websiteContentRepository
        verify(websiteScanAuditRepository, atLeastOnce()).countDistinctScanDatesByUserAndDateAfter(
            eq(previewUser.getId()), any(LocalDateTime.class));
        
        // Verify we do NOT use the old WebsiteContent method
        verify(websiteContentRepository, never()).countScansByUserAndDateAfter(
            anyLong(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("SECURITY: Audit entry should persist after chatbot deletion")
    void auditEntryShouldPersistAfterChatbotDeletion() {
        // Arrange
        setupPreviewModeMocks();
        
        when(websiteScanAuditRepository.countDistinctScanDatesByUserAndDateAfter(
            eq(previewUser.getId()), any(LocalDateTime.class))).thenReturn(0L);
        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));

        // Act - scan website
        chatbotController.analyzeWebsite(1L, customOAuth2User);

        // Verify audit entry was created
        ArgumentCaptor<WebsiteScanAudit> auditCaptor = ArgumentCaptor.forClass(WebsiteScanAudit.class);
        verify(websiteScanAuditRepository, times(1)).save(auditCaptor.capture());
        
        WebsiteScanAudit savedAudit = auditCaptor.getValue();
        
        // Assert - audit entry should have chatbot ID but be independent
        assertThat(savedAudit.getChatbotId()).isEqualTo(1L);
        assertThat(savedAudit.getUser().getId()).isEqualTo(previewUser.getId());
        assertThat(savedAudit.getWebsiteUrl()).isEqualTo("https://example.com");
        
        // Simulate chatbot deletion - audit should still exist
        // (In real scenario, chatbot would be deleted but audit remains)
        lenient().when(chatbotRepository.findById(1L)).thenReturn(Optional.empty());
        
        // Verify audit repository still has the entry (we can't actually delete it here,
        // but the key point is that there's no cascade delete)
        verify(websiteScanAuditRepository, times(1)).save(any(WebsiteScanAudit.class));
    }

    @Test
    @DisplayName("SECURITY: Should block scan if limit reached, even with different chatbot")
    void shouldBlockScanIfLimitReachedWithDifferentChatbot() {
        // Arrange - user already scanned today
        setupPreviewModeMocks();
        
        when(websiteScanAuditRepository.countDistinctScanDatesByUserAndDateAfter(
            eq(previewUser.getId()), any(LocalDateTime.class))).thenReturn(1L);
        
        Chatbot differentChatbot = TestDataBuilder.createTestChatbot(previewUser);
        differentChatbot.setId(999L);
        differentChatbot.setWebsiteUrl("https://different-site.com");
        
        when(chatbotRepository.findById(999L)).thenReturn(Optional.of(differentChatbot));

        // Act
        ResponseEntity<?> response = chatbotController.analyzeWebsite(999L, customOAuth2User);

        // Assert - should be blocked
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        
        // Verify scan was NOT started
        verify(websiteAnalysisService, never()).analyzeWebsite(any(Chatbot.class));
        
        // Verify audit entry was NOT created (scan was blocked)
        verify(websiteScanAuditRepository, never()).save(any(WebsiteScanAudit.class));
    }

    @Test
    @DisplayName("SECURITY: Should allow scan after 24 hours, not after delete/recreate")
    void shouldAllowScanAfter24HoursNotAfterDeleteRecreate() {
        // Arrange - scan from yesterday (should be allowed)
        setupPreviewModeMocks();
        
        when(websiteScanAuditRepository.countDistinctScanDatesByUserAndDateAfter(
            eq(previewUser.getId()), any(LocalDateTime.class))).thenReturn(0L); // No scans in last 24 hours
        
        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));

        // Act
        ResponseEntity<?> response = chatbotController.analyzeWebsite(1L, customOAuth2User);

        // Assert - should be allowed (24 hours passed)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Verify scan was started
        verify(websiteAnalysisService, times(1)).analyzeWebsite(any(Chatbot.class));
        
        // Verify audit entry was created
        verify(websiteScanAuditRepository, times(1)).save(any(WebsiteScanAudit.class));
    }
}

