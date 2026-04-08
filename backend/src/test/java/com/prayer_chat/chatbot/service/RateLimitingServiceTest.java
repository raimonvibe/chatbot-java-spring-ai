package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.config.PlanLimits;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.MessageRepository;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import com.prayer_chat.chatbot.repository.WebsiteScanAuditRepository;
import com.prayer_chat.chatbot.service.BillingModeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Tests for RateLimitingService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitingService Tests")
class RateLimitingServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private WebsiteScanAuditRepository websiteScanAuditRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private BillingModeService billingModeService;

    @InjectMocks
    private RateLimitingService rateLimitingService;

    private User previewUser;
    private User paidUser;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(billingModeService.isBillingEnabled()).thenReturn(true);
        org.mockito.Mockito.lenient().when(billingModeService.effectiveMessagesPerDay(any()))
            .thenAnswer(inv -> PlanLimits.messagesPerDay(inv.getArgument(0)));
        org.mockito.Mockito.lenient().when(billingModeService.effectiveDailyScanLimit(any()))
            .thenAnswer(inv -> PlanLimits.dailyScanLimit(inv.getArgument(0)));
        org.mockito.Mockito.lenient().when(billingModeService.effectiveMonthlyScanQuota(any()))
            .thenAnswer(inv -> PlanLimits.monthlyScanQuota(inv.getArgument(0)));

        previewUser = new User();
        previewUser.setId(1L);
        previewUser.setEmail("preview@example.com");

        paidUser = new User();
        paidUser.setId(2L);
        paidUser.setEmail("paid@example.com");
    }

    @Test
    @DisplayName("Should allow preview user to send message within limit")
    void shouldAllowPreviewUserWithinMessageLimit() {
        when(subscriptionRepository.findByUserId(previewUser.getId())).thenReturn(Optional.empty());
        when(accessControlService.isPreviewMode(previewUser)).thenReturn(true);
        when(messageRepository.countUserMessagesTodayByUserId(anyLong())).thenReturn(5L);

        RateLimitingService.RateLimitResult result = rateLimitingService.checkMessageLimit(previewUser);

        assertTrue(result.isAllowed());
        assertEquals(10, result.getLimit());
        assertEquals(5, result.getCurrent());
        assertTrue(result.isPreviewMode());
        assertEquals("message", result.getType());
    }

    @Test
    @DisplayName("Should block preview user when message limit reached")
    void shouldBlockPreviewUserWhenMessageLimitReached() {
        when(subscriptionRepository.findByUserId(previewUser.getId())).thenReturn(Optional.empty());
        when(accessControlService.isPreviewMode(previewUser)).thenReturn(true);
        when(messageRepository.countUserMessagesTodayByUserId(anyLong())).thenReturn(10L);

        RateLimitingService.RateLimitResult result = rateLimitingService.checkMessageLimit(previewUser);

        // Assert
        assertFalse(result.isAllowed());
        assertEquals(10, result.getLimit());
        assertEquals(10, result.getCurrent());
        assertTrue(result.isPreviewMode());
        assertTrue(result.getErrorMessage().contains("Preview mode allows 10 messages per day"));
    }

    @Test
    @DisplayName("Should allow paid user messages within plan limit (ENTERPRISE = 2000/day)")
    void shouldAllowPaidUserMessagesWithinPlanLimit() {
        Subscription ent = new Subscription();
        ent.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        ent.setPlan(Subscription.SubscriptionPlan.ENTERPRISE);
        when(subscriptionRepository.findByUserId(paidUser.getId())).thenReturn(Optional.of(ent));
        when(accessControlService.isPreviewMode(paidUser)).thenReturn(false);
        when(messageRepository.countUserMessagesTodayByUserId(anyLong())).thenReturn(1000L);

        RateLimitingService.RateLimitResult result = rateLimitingService.checkMessageLimit(paidUser);

        assertTrue(result.isAllowed());
        assertEquals(2000, result.getLimit());
        assertEquals(1000, result.getCurrent());
        assertFalse(result.isPreviewMode());
    }

    @Test
    @DisplayName("Should allow preview user to scan within limit")
    void shouldAllowPreviewUserWithinScanLimit() {
        when(subscriptionRepository.findByUserId(previewUser.getId())).thenReturn(Optional.empty());
        when(accessControlService.isPreviewMode(previewUser)).thenReturn(true);
        when(websiteScanAuditRepository.countScansByUserAndDateAfter(anyLong(), any(LocalDateTime.class))).thenReturn(0L);
        when(websiteScanAuditRepository.countScansByUserAndScanDateAfter(anyLong(), any(LocalDateTime.class))).thenReturn(0L);

        RateLimitingService.RateLimitResult result = rateLimitingService.checkScanLimit(previewUser);

        // Assert
        assertTrue(result.isAllowed());
        assertEquals(1, result.getLimit());
        assertEquals(0, result.getCurrent());
        assertTrue(result.isPreviewMode());
        assertEquals("scan", result.getType());
    }

    @Test
    @DisplayName("Should block preview user when scan limit reached")
    void shouldBlockPreviewUserWhenScanLimitReached() {
        when(subscriptionRepository.findByUserId(previewUser.getId())).thenReturn(Optional.empty());
        when(accessControlService.isPreviewMode(previewUser)).thenReturn(true);
        when(websiteScanAuditRepository.countScansByUserAndDateAfter(anyLong(), any(LocalDateTime.class))).thenReturn(1L);
        when(websiteScanAuditRepository.countScansByUserAndScanDateAfter(anyLong(), any(LocalDateTime.class))).thenReturn(1L);

        RateLimitingService.RateLimitResult result = rateLimitingService.checkScanLimit(previewUser);

        // Assert
        assertFalse(result.isAllowed());
        assertEquals(1, result.getLimit());
        assertEquals(1, result.getCurrent());
        assertTrue(result.isPreviewMode());
        assertTrue(result.getErrorMessage().contains("Upgrade to run more scans"));
    }

    @Test
    @DisplayName("Should allow paid user (PRO) up to 10 scans per day within monthly quota")
    void shouldAllowPaidUserUpTo10ScansPerDay() {
        Subscription pro = new Subscription();
        pro.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        pro.setPlan(Subscription.SubscriptionPlan.PRO);
        when(subscriptionRepository.findByUserId(paidUser.getId())).thenReturn(Optional.of(pro));
        when(accessControlService.isPreviewMode(paidUser)).thenReturn(false);
        when(websiteScanAuditRepository.countScansByUserAndDateAfter(anyLong(), any(LocalDateTime.class))).thenReturn(5L);
        when(websiteScanAuditRepository.countScansByUserAndScanDateAfter(anyLong(), any(LocalDateTime.class))).thenReturn(5L);

        RateLimitingService.RateLimitResult result = rateLimitingService.checkScanLimit(paidUser);

        assertTrue(result.isAllowed());
        assertEquals(10, result.getLimit());
        assertEquals(5, result.getCurrent());
        assertFalse(result.isPreviewMode());
    }

    @Test
    @DisplayName("Should block paid user when daily scan limit reached (PRO = 10/day)")
    void shouldBlockPaidUserWhenScanLimitReached() {
        Subscription pro = new Subscription();
        pro.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        pro.setPlan(Subscription.SubscriptionPlan.PRO);
        when(subscriptionRepository.findByUserId(paidUser.getId())).thenReturn(Optional.of(pro));
        when(accessControlService.isPreviewMode(paidUser)).thenReturn(false);
        when(websiteScanAuditRepository.countScansByUserAndDateAfter(anyLong(), any(LocalDateTime.class))).thenReturn(10L);
        when(websiteScanAuditRepository.countScansByUserAndScanDateAfter(anyLong(), any(LocalDateTime.class))).thenReturn(10L);

        RateLimitingService.RateLimitResult result = rateLimitingService.checkScanLimit(paidUser);

        assertFalse(result.isAllowed());
        assertEquals(10, result.getLimit());
        assertEquals(10, result.getCurrent());
        assertFalse(result.isPreviewMode());
        assertTrue(result.getErrorMessage().toLowerCase().contains("scan limit"));
    }

    @Test
    @DisplayName("Should handle null message count gracefully")
    void shouldHandleNullMessageCount() {
        when(subscriptionRepository.findByUserId(previewUser.getId())).thenReturn(Optional.empty());
        when(accessControlService.isPreviewMode(previewUser)).thenReturn(true);
        when(messageRepository.countUserMessagesTodayByUserId(anyLong())).thenReturn(null);

        // Act
        RateLimitingService.RateLimitResult result = rateLimitingService.checkMessageLimit(previewUser);

        // Assert
        assertTrue(result.isAllowed()); // Should treat null as 0
        assertEquals(0, result.getCurrent());
    }

    @Test
    @DisplayName("Should handle null scan count gracefully")
    void shouldHandleNullScanCount() {
        when(subscriptionRepository.findByUserId(previewUser.getId())).thenReturn(Optional.empty());
        when(accessControlService.isPreviewMode(previewUser)).thenReturn(true);
        when(websiteScanAuditRepository.countScansByUserAndDateAfter(anyLong(), any(LocalDateTime.class))).thenReturn(null);
        when(websiteScanAuditRepository.countScansByUserAndScanDateAfter(anyLong(), any(LocalDateTime.class))).thenReturn(0L);

        // Act
        RateLimitingService.RateLimitResult result = rateLimitingService.checkScanLimit(previewUser);

        // Assert
        assertTrue(result.isAllowed()); // Should treat null as 0
        assertEquals(0, result.getCurrent());
    }

    @Test
    @DisplayName("Should return correct max messages per day for FREE plan (10)")
    void shouldReturnCorrectMaxMessagesForPreviewUser() {
        when(subscriptionRepository.findByUserId(previewUser.getId())).thenReturn(Optional.empty());
        int maxMessages = rateLimitingService.getMaxMessagesPerDay(previewUser);
        assertEquals(10, maxMessages);
    }

    @Test
    @DisplayName("Should return correct max messages per day for PRO plan (500)")
    void shouldReturnCorrectMaxMessagesForPaidUser() {
        Subscription pro = new Subscription();
        pro.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        pro.setPlan(Subscription.SubscriptionPlan.PRO);
        when(subscriptionRepository.findByUserId(paidUser.getId())).thenReturn(Optional.of(pro));
        int maxMessages = rateLimitingService.getMaxMessagesPerDay(paidUser);
        assertEquals(500, maxMessages);
    }

    @Test
    @DisplayName("Should return correct max scans per day for FREE plan (1)")
    void shouldReturnCorrectMaxScansForPreviewUser() {
        when(subscriptionRepository.findByUserId(previewUser.getId())).thenReturn(Optional.empty());
        int maxScans = rateLimitingService.getMaxScansPerDay(previewUser);
        assertEquals(1, maxScans);
    }

    @Test
    @DisplayName("Should return correct max scans per day for PRO plan (10)")
    void shouldReturnCorrectMaxScansForPaidUser() {
        Subscription pro = new Subscription();
        pro.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        pro.setPlan(Subscription.SubscriptionPlan.PRO);
        when(subscriptionRepository.findByUserId(paidUser.getId())).thenReturn(Optional.of(pro));
        int maxScans = rateLimitingService.getMaxScansPerDay(paidUser);
        assertEquals(10, maxScans);
    }
}

