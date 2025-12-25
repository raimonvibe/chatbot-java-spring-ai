package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.MessageRepository;
import com.prayer_chat.chatbot.repository.WebsiteScanAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

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

    @InjectMocks
    private RateLimitingService rateLimitingService;

    private User previewUser;
    private User paidUser;

    @BeforeEach
    void setUp() {
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
        // Arrange
        when(accessControlService.isPreviewMode(previewUser)).thenReturn(true);
        when(messageRepository.countUserMessagesTodayByUserId(anyLong())).thenReturn(5L); // 5 messages today

        // Act
        RateLimitingService.RateLimitResult result = rateLimitingService.checkMessageLimit(previewUser);

        // Assert
        assertTrue(result.isAllowed());
        assertEquals(10, result.getLimit());
        assertEquals(5, result.getCurrent());
        assertTrue(result.isPreviewMode());
        assertEquals("message", result.getType());
    }

    @Test
    @DisplayName("Should block preview user when message limit reached")
    void shouldBlockPreviewUserWhenMessageLimitReached() {
        // Arrange
        when(accessControlService.isPreviewMode(previewUser)).thenReturn(true);
        when(messageRepository.countUserMessagesTodayByUserId(anyLong())).thenReturn(10L); // 10 messages today (at limit)

        // Act
        RateLimitingService.RateLimitResult result = rateLimitingService.checkMessageLimit(previewUser);

        // Assert
        assertFalse(result.isAllowed());
        assertEquals(10, result.getLimit());
        assertEquals(10, result.getCurrent());
        assertTrue(result.isPreviewMode());
        assertTrue(result.getErrorMessage().contains("Preview mode allows 10 messages per day"));
    }

    @Test
    @DisplayName("Should allow paid user unlimited messages")
    void shouldAllowPaidUserUnlimitedMessages() {
        // Arrange
        when(accessControlService.isPreviewMode(paidUser)).thenReturn(false);
        when(messageRepository.countUserMessagesTodayByUserId(anyLong())).thenReturn(1000L); // 1000 messages today

        // Act
        RateLimitingService.RateLimitResult result = rateLimitingService.checkMessageLimit(paidUser);

        // Assert
        assertTrue(result.isAllowed());
        assertEquals(Integer.MAX_VALUE, result.getLimit());
        assertEquals(1000, result.getCurrent());
        assertFalse(result.isPreviewMode());
    }

    @Test
    @DisplayName("Should allow preview user to scan within limit")
    void shouldAllowPreviewUserWithinScanLimit() {
        // Arrange
        when(accessControlService.isPreviewMode(previewUser)).thenReturn(true);
        when(websiteScanAuditRepository.countDistinctScanDatesByUserAndDateAfter(anyLong(), any(LocalDateTime.class)))
            .thenReturn(0L); // 0 scans in last day

        // Act
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
        // Arrange
        when(accessControlService.isPreviewMode(previewUser)).thenReturn(true);
        when(websiteScanAuditRepository.countDistinctScanDatesByUserAndDateAfter(anyLong(), any(LocalDateTime.class)))
            .thenReturn(1L); // 1 scan in last day (at limit)

        // Act
        RateLimitingService.RateLimitResult result = rateLimitingService.checkScanLimit(previewUser);

        // Assert
        assertFalse(result.isAllowed());
        assertEquals(1, result.getLimit());
        assertEquals(1, result.getCurrent());
        assertTrue(result.isPreviewMode());
        assertTrue(result.getErrorMessage().contains("Preview mode allows 1 scan per day"));
    }

    @Test
    @DisplayName("Should allow paid user up to 10 scans per day")
    void shouldAllowPaidUserUpTo10ScansPerDay() {
        // Arrange
        when(accessControlService.isPreviewMode(paidUser)).thenReturn(false);
        when(websiteScanAuditRepository.countDistinctScanDatesByUserAndDateAfter(anyLong(), any(LocalDateTime.class)))
            .thenReturn(5L); // 5 scans in last day

        // Act
        RateLimitingService.RateLimitResult result = rateLimitingService.checkScanLimit(paidUser);

        // Assert
        assertTrue(result.isAllowed());
        assertEquals(10, result.getLimit());
        assertEquals(5, result.getCurrent());
        assertFalse(result.isPreviewMode());
    }

    @Test
    @DisplayName("Should block paid user when scan limit reached (10 scans)")
    void shouldBlockPaidUserWhenScanLimitReached() {
        // Arrange
        when(accessControlService.isPreviewMode(paidUser)).thenReturn(false);
        when(websiteScanAuditRepository.countDistinctScanDatesByUserAndDateAfter(anyLong(), any(LocalDateTime.class)))
            .thenReturn(10L); // 10 scans in last day (at limit)

        // Act
        RateLimitingService.RateLimitResult result = rateLimitingService.checkScanLimit(paidUser);

        // Assert
        assertFalse(result.isAllowed());
        assertEquals(10, result.getLimit());
        assertEquals(10, result.getCurrent());
        assertFalse(result.isPreviewMode());
        assertTrue(result.getErrorMessage().contains("Daily scan limit reached"));
    }

    @Test
    @DisplayName("Should handle null message count gracefully")
    void shouldHandleNullMessageCount() {
        // Arrange
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
        // Arrange
        when(accessControlService.isPreviewMode(previewUser)).thenReturn(true);
        when(websiteScanAuditRepository.countDistinctScanDatesByUserAndDateAfter(anyLong(), any(LocalDateTime.class)))
            .thenReturn(null);

        // Act
        RateLimitingService.RateLimitResult result = rateLimitingService.checkScanLimit(previewUser);

        // Assert
        assertTrue(result.isAllowed()); // Should treat null as 0
        assertEquals(0, result.getCurrent());
    }

    @Test
    @DisplayName("Should return correct max messages per day for preview user")
    void shouldReturnCorrectMaxMessagesForPreviewUser() {
        // Arrange
        when(accessControlService.isPreviewMode(previewUser)).thenReturn(true);
        // Act
        int maxMessages = rateLimitingService.getMaxMessagesPerDay(previewUser);

        // Assert
        assertEquals(10, maxMessages);
    }

    @Test
    @DisplayName("Should return correct max messages per day for paid user")
    void shouldReturnCorrectMaxMessagesForPaidUser() {
        // Arrange
        when(accessControlService.isPreviewMode(paidUser)).thenReturn(false);
        // Act
        int maxMessages = rateLimitingService.getMaxMessagesPerDay(paidUser);

        // Assert
        assertEquals(Integer.MAX_VALUE, maxMessages);
    }

    @Test
    @DisplayName("Should return correct max scans per day for preview user")
    void shouldReturnCorrectMaxScansForPreviewUser() {
        // Arrange
        when(accessControlService.isPreviewMode(previewUser)).thenReturn(true);
        // Act
        int maxScans = rateLimitingService.getMaxScansPerDay(previewUser);

        // Assert
        assertEquals(1, maxScans);
    }

    @Test
    @DisplayName("Should return correct max scans per day for paid user")
    void shouldReturnCorrectMaxScansForPaidUser() {
        // Arrange
        when(accessControlService.isPreviewMode(paidUser)).thenReturn(false);
        // Act
        int maxScans = rateLimitingService.getMaxScansPerDay(paidUser);

        // Assert
        assertEquals(10, maxScans);
    }
}

