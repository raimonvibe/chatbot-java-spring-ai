package com.tjanabot.chatbot.unit.service;

import com.tjanabot.chatbot.helpers.TestDataBuilder;
import com.tjanabot.chatbot.model.AuditLog;
import com.tjanabot.chatbot.model.User;
import com.tjanabot.chatbot.repository.AuditLogRepository;
import com.tjanabot.chatbot.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.AsyncResult;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService Unit Tests")
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = TestDataBuilder.createTestUser();
        testUser.setId(1L);
    }

    @Test
    @DisplayName("Should log audit event successfully")
    void shouldLogAuditEvent() {
        // Arrange
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        // Act
        auditService.logWithIpAddress(
            AuditLog.EventType.LOGIN_SUCCESS,
            AuditLog.Severity.INFO,
            "User logged in",
            testUser,
            "192.168.1.1"
        );

        // Assert
        verify(auditLogRepository, timeout(1000).times(1)).save(captor.capture());
        AuditLog savedLog = captor.getValue();
        assertThat(savedLog.getEventType()).isEqualTo(AuditLog.EventType.LOGIN_SUCCESS);
        assertThat(savedLog.getSeverity()).isEqualTo(AuditLog.Severity.INFO);
        assertThat(savedLog.getDescription()).isEqualTo("User logged in");
        assertThat(savedLog.getUser()).isEqualTo(testUser);
        assertThat(savedLog.getIpAddress()).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("Should detect too many failed logins")
    void shouldDetectTooManyFailedLogins() {
        // Arrange
        when(auditLogRepository.countByUserIdAndEventTypeAndTimestampAfter(
            eq(1L),
            eq(AuditLog.EventType.LOGIN_FAILURE),
            any(LocalDateTime.class)
        )).thenReturn(6L);

        // Act
        boolean result = auditService.hasTooManyFailedLogins(1L, 5, 60);

        // Assert
        assertThat(result).isTrue();
        verify(auditLogRepository, times(1))
            .countByUserIdAndEventTypeAndTimestampAfter(eq(1L), eq(AuditLog.EventType.LOGIN_FAILURE), any());
    }

    @Test
    @DisplayName("Should not flag normal number of failed logins")
    void shouldNotFlagNormalFailedLogins() {
        // Arrange
        when(auditLogRepository.countByUserIdAndEventTypeAndTimestampAfter(
            eq(1L),
            eq(AuditLog.EventType.LOGIN_FAILURE),
            any(LocalDateTime.class)
        )).thenReturn(3L);

        // Act
        boolean result = auditService.hasTooManyFailedLogins(1L, 5, 60);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should detect too many payment failures")
    void shouldDetectTooManyPaymentFailures() {
        // Arrange
        when(auditLogRepository.countByUserIdAndEventTypeAndTimestampAfter(
            eq(1L),
            eq(AuditLog.EventType.PAYMENT_FAILED),
            any(LocalDateTime.class)
        )).thenReturn(4L);

        // Act
        boolean result = auditService.hasTooManyPaymentFailures(1L, 3, 30);

        // Assert
        assertThat(result).isTrue();
        verify(auditLogRepository, times(1))
            .countByUserIdAndEventTypeAndTimestampAfter(eq(1L), eq(AuditLog.EventType.PAYMENT_FAILED), any());
    }

    @Test
    @DisplayName("Should retrieve audit logs for user")
    void shouldRetrieveAuditLogsForUser() {
        // Arrange
        List<AuditLog> mockLogs = Arrays.asList(
            TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.LOGIN_SUCCESS),
            TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.CHATBOT_CREATED)
        );
        when(auditLogRepository.findByUserIdOrderByTimestampDesc(1L)).thenReturn(mockLogs);

        // Act
        List<AuditLog> logs = auditService.getAuditLogsForUser(1L);

        // Assert
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getEventType()).isEqualTo(AuditLog.EventType.LOGIN_SUCCESS);
        verify(auditLogRepository, times(1)).findByUserIdOrderByTimestampDesc(1L);
    }

    @Test
    @DisplayName("Should retrieve audit logs by event type")
    void shouldRetrieveAuditLogsByEventType() {
        // Arrange
        List<AuditLog> mockLogs = Arrays.asList(
            TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.PAYMENT_FAILED),
            TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.PAYMENT_FAILED)
        );
        when(auditLogRepository.findByEventTypeOrderByTimestampDesc(AuditLog.EventType.PAYMENT_FAILED))
            .thenReturn(mockLogs);

        // Act
        List<AuditLog> logs = auditService.getAuditLogsByEventType(AuditLog.EventType.PAYMENT_FAILED);

        // Assert
        assertThat(logs).hasSize(2);
        assertThat(logs).allMatch(log -> log.getEventType() == AuditLog.EventType.PAYMENT_FAILED);
        verify(auditLogRepository, times(1))
            .findByEventTypeOrderByTimestampDesc(AuditLog.EventType.PAYMENT_FAILED);
    }

    @Test
    @DisplayName("Should retrieve audit logs by severity level")
    void shouldRetrieveAuditLogsBySeverity() {
        // Arrange
        List<AuditLog> mockLogs = Arrays.asList(
            TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.SECURITY_ALERT, AuditLog.Severity.CRITICAL),
            TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.SUSPICIOUS_ACTIVITY, AuditLog.Severity.CRITICAL)
        );
        when(auditLogRepository.findBySeverityOrderByTimestampDesc(AuditLog.Severity.CRITICAL))
            .thenReturn(mockLogs);

        // Act
        List<AuditLog> logs = auditService.getAuditLogsBySeverity(AuditLog.Severity.CRITICAL);

        // Assert
        assertThat(logs).hasSize(2);
        assertThat(logs).allMatch(log -> log.getSeverity() == AuditLog.Severity.CRITICAL);
        verify(auditLogRepository, times(1))
            .findBySeverityOrderByTimestampDesc(AuditLog.Severity.CRITICAL);
    }

    @Test
    @DisplayName("Should retrieve audit logs within date range")
    void shouldRetrieveAuditLogsInDateRange() {
        // Arrange
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();
        List<AuditLog> mockLogs = Arrays.asList(
            TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.LOGIN_SUCCESS)
        );
        when(auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(start, end))
            .thenReturn(mockLogs);

        // Act
        List<AuditLog> logs = auditService.getAuditLogsBetween(start, end);

        // Assert
        assertThat(logs).hasSize(1);
        verify(auditLogRepository, times(1)).findByTimestampBetweenOrderByTimestampDesc(start, end);
    }

    @Test
    @DisplayName("Should handle null user in audit log")
    void shouldHandleNullUser() {
        // Arrange
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        // Act
        auditService.logWithIpAddress(
            AuditLog.EventType.SYSTEM_EVENT,
            AuditLog.Severity.INFO,
            "System started",
            null,
            null
        );

        // Assert
        verify(auditLogRepository, timeout(1000).times(1)).save(captor.capture());
        AuditLog savedLog = captor.getValue();
        assertThat(savedLog.getUser()).isNull();
        assertThat(savedLog.getEventType()).isEqualTo(AuditLog.EventType.SYSTEM_EVENT);
    }
}
