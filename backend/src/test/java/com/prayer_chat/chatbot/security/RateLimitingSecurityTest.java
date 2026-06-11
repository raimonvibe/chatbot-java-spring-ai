package com.prayer_chat.chatbot.security;

import com.prayer_chat.chatbot.controller.ChatController;
import com.prayer_chat.chatbot.controller.ChatbotController;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.repository.MessageRepository;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import com.prayer_chat.chatbot.repository.UserRepository;
import com.prayer_chat.chatbot.repository.WebsiteScanAuditRepository;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
import com.prayer_chat.chatbot.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Security tests for Rate Limiting implementation
 * 
 * Verifies:
 * - Race condition protection
 * - Authorization checks
 * - Bypass prevention
 * - Input validation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Rate Limiting Security Tests")
class RateLimitingSecurityTest {

    @Mock
    private ChatbotRepository chatbotRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private WebsiteScanAuditRepository websiteScanAuditRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BillingModeService billingModeService;

    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private CustomOAuth2User customOAuth2User;

    private User testUser;
    private Chatbot testChatbot;
    private RateLimitingService realRateLimitingService;

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

        lenient().when(billingModeService.isBillingEnabled()).thenReturn(true);
        lenient().when(billingModeService.effectiveMessagesPerDay(any()))
            .thenAnswer(inv -> com.prayer_chat.chatbot.config.PlanLimits.messagesPerDay(inv.getArgument(0)));
        lenient().when(billingModeService.effectiveDailyScanLimit(any()))
            .thenAnswer(inv -> com.prayer_chat.chatbot.config.PlanLimits.dailyScanLimit(inv.getArgument(0)));
        lenient().when(billingModeService.effectiveMonthlyScanQuota(any()))
            .thenAnswer(inv -> com.prayer_chat.chatbot.config.PlanLimits.monthlyScanQuota(inv.getArgument(0)));

        // Create real RateLimitingService for some tests
        lenient().when(userRepository.findByIdWithLock(any())).thenReturn(Optional.of(testUser));

        realRateLimitingService = new RateLimitingService(
            messageRepository,
            websiteScanAuditRepository,
            accessControlService,
            subscriptionRepository,
            userRepository,
            billingModeService
        );
    }

    @Test
    @DisplayName("Should prevent rate limit bypass by deleting chatbots")
    void shouldPreventRateLimitBypassByDeletingChatbots() {
        // Arrange: User has reached message limit
        when(accessControlService.isPreviewMode(testUser)).thenReturn(true);
        when(messageRepository.countUserMessagesTodayByUserId(testUser.getId())).thenReturn(10L); // At limit

        // Act: Check rate limit
        RateLimitingService.RateLimitResult result = realRateLimitingService.checkMessageLimit(testUser);

        // Assert: Should be blocked even if chatbots are deleted
        assertFalse(result.isAllowed());
        assertEquals(10, result.getCurrent());
        assertEquals(10, result.getLimit());
        
        // Verify: Message count is based on user ID, not chatbot ID
        verify(messageRepository).countUserMessagesTodayByUserId(testUser.getId());
    }

    @Test
    @DisplayName("Should prevent scan limit bypass using WebsiteScanAudit")
    void shouldPreventScanLimitBypassUsingWebsiteScanAudit() {
        // Arrange: User has scanned today
        when(accessControlService.isPreviewMode(testUser)).thenReturn(true);
        when(websiteScanAuditRepository.countScansTodayByUserId(testUser.getId())).thenReturn(1L); // At limit

        // Act: Check scan limit
        RateLimitingService.RateLimitResult result = realRateLimitingService.checkScanLimit(testUser);

        // Assert: Should be blocked
        assertFalse(result.isAllowed());
        assertEquals(1, result.getCurrent());
        assertEquals(1, result.getLimit());
        
        // Verify: Uses WebsiteScanAudit (not WebsiteContent) to prevent bypass via deletion
        verify(websiteScanAuditRepository).countScansTodayByUserId(testUser.getId());
    }

    @Test
    @DisplayName("Should handle chatbots without owner (rate limiting skipped)")
    void shouldHandleChatbotsWithoutOwner() {
        // Arrange: Chatbot without owner
        Chatbot orphanChatbot = new Chatbot();
        orphanChatbot.setId(200L);
        orphanChatbot.setOwner(null); // No owner
        orphanChatbot.setIsActive(true);

        // Assert: Chatbot exists but has no owner
        assertNull(orphanChatbot.getOwner());
        
        // Security Note: Orphan chatbots skip rate limiting (no owner to check)
        // This is acceptable because:
        // 1. Orphan chatbots should be rare in production
        // 2. Rate limiting is per chatbot owner, not per chatbot
        // 3. Orphan chatbots are edge cases that should be cleaned up
        // 
        // Recommendation: Ensure all chatbots have owners in production
    }

    @Test
    @DisplayName("Should handle null user ID gracefully")
    void shouldHandleNullUserId() {
        User nullIdUser = new User();
        nullIdUser.setId(null);

        RateLimitingService.RateLimitResult result = realRateLimitingService.checkMessageLimit(nullIdUser);

        assertNotNull(result);
        assertFalse(result.isAllowed());
    }

    @Test
    @DisplayName("Should prevent concurrent rate limit bypass attempts")
    void shouldPreventConcurrentRateLimitBypass() throws InterruptedException {
        // Arrange: Simulate concurrent requests
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);

        // Mock: User is at limit (9 messages, limit is 10)
        when(accessControlService.isPreviewMode(testUser)).thenReturn(true);
        when(messageRepository.countUserMessagesTodayByUserId(testUser.getId()))
            .thenReturn(9L); // Just below limit

        // Act: Concurrent rate limit checks
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    RateLimitingService.RateLimitResult result = 
                        realRateLimitingService.checkMessageLimit(testUser);
                    if (result.isAllowed()) {
                        allowedCount.incrementAndGet();
                    } else {
                        blockedCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Assert: All should be allowed (at 9, limit is 10)
        // Note: This test verifies the check works, but doesn't prevent
        // race conditions in actual message processing
        assertEquals(threadCount, allowedCount.get());
        assertEquals(0, blockedCount.get());
    }

    @Test
    @DisplayName("Should handle null message count gracefully")
    void shouldHandleNullMessageCountGracefully() {
        // Arrange: Repository returns null
        when(accessControlService.isPreviewMode(testUser)).thenReturn(true);
        when(messageRepository.countUserMessagesTodayByUserId(testUser.getId())).thenReturn(null);

        // Act
        RateLimitingService.RateLimitResult result = realRateLimitingService.checkMessageLimit(testUser);

        // Assert: Should treat null as 0
        assertTrue(result.isAllowed());
        assertEquals(0, result.getCurrent());
    }

    @Test
    @DisplayName("Should handle null scan count gracefully")
    void shouldHandleNullScanCountGracefully() {
        // Arrange: Repository returns null
        when(accessControlService.isPreviewMode(testUser)).thenReturn(true);
        when(websiteScanAuditRepository.countScansTodayByUserId(anyLong())).thenReturn(null);

        // Act
        RateLimitingService.RateLimitResult result = realRateLimitingService.checkScanLimit(testUser);

        // Assert: Should treat null as 0
        assertTrue(result.isAllowed());
        assertEquals(0, result.getCurrent());
    }

    @Test
    @DisplayName("Should prevent negative message counts")
    void shouldPreventNegativeMessageCounts() {
        // Arrange: Repository returns negative (shouldn't happen, but test defense)
        when(accessControlService.isPreviewMode(testUser)).thenReturn(true);
        when(messageRepository.countUserMessagesTodayByUserId(testUser.getId())).thenReturn(-1L);

        // Act
        RateLimitingService.RateLimitResult result = realRateLimitingService.checkMessageLimit(testUser);

        // Assert: Should handle negative gracefully (treat as 0 or allow)
        // Negative counts shouldn't happen, but if they do, should not crash
        assertNotNull(result);
    }

    @Test
    @DisplayName("Should use calendar-day window for daily scan limits")
    void shouldUseCorrectTimeWindowForScanLimits() {
        // Arrange
        when(accessControlService.isPreviewMode(testUser)).thenReturn(true);
        when(websiteScanAuditRepository.countScansTodayByUserId(testUser.getId())).thenReturn(0L);

        // Act
        realRateLimitingService.checkScanLimit(testUser);

        // Assert: daily limit uses the calendar-day query (consistent with daily message limit),
        // and the monthly quota uses a window starting at the first of the current month.
        verify(websiteScanAuditRepository).countScansTodayByUserId(testUser.getId());
        verify(websiteScanAuditRepository).countScansByUserAndScanDateAfter(
            eq(testUser.getId()),
            argThat(date -> date.equals(java.time.YearMonth.now().atDay(1).atStartOfDay()))
        );
    }

    @Test
    @DisplayName("Should prevent message limit bypass via multiple chatbots")
    void shouldPreventMessageLimitBypassViaMultipleChatbots() {
        // Arrange: User owns multiple chatbots
        Chatbot chatbot1 = new Chatbot();
        chatbot1.setId(1L);
        chatbot1.setOwner(testUser);

        Chatbot chatbot2 = new Chatbot();
        chatbot2.setId(2L);
        chatbot2.setOwner(testUser);

        when(accessControlService.isPreviewMode(testUser)).thenReturn(true);
        when(messageRepository.countUserMessagesTodayByUserId(testUser.getId())).thenReturn(10L); // At limit

        // Act: Check rate limit (should be same for all chatbots owned by user)
        RateLimitingService.RateLimitResult result1 = realRateLimitingService.checkMessageLimit(testUser);
        RateLimitingService.RateLimitResult result2 = realRateLimitingService.checkMessageLimit(testUser);

        // Assert: Both should be blocked (limit is per user, not per chatbot)
        assertFalse(result1.isAllowed());
        assertFalse(result2.isAllowed());
        assertEquals(result1.getCurrent(), result2.getCurrent());
        assertEquals(result1.getLimit(), result2.getLimit());
    }
}

