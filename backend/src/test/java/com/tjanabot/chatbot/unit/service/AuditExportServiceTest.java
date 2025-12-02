package com.tjanabot.chatbot.unit.service;

import com.tjanabot.chatbot.helpers.TestDataBuilder;
import com.tjanabot.chatbot.model.AuditLog;
import com.tjanabot.chatbot.model.User;
import com.tjanabot.chatbot.service.AuditExportService;
import com.tjanabot.chatbot.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditExportService Unit Tests")
class AuditExportServiceTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuditExportService auditExportService;

    private User testUser;
    private List<AuditLog> sampleLogs;

    @BeforeEach
    void setUp() {
        testUser = TestDataBuilder.createTestUser();
        testUser.setId(1L);

        AuditLog log1 = TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.LOGIN_SUCCESS);
        log1.setId(1L);
        log1.setTimestamp(LocalDateTime.of(2025, 12, 1, 10, 30));
        log1.setIpAddress("192.168.1.100");

        AuditLog log2 = TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.CHATBOT_CREATED);
        log2.setId(2L);
        log2.setTimestamp(LocalDateTime.of(2025, 12, 1, 11, 0));
        log2.setIpAddress("192.168.1.100");

        sampleLogs = Arrays.asList(log1, log2);
    }

    @Test
    @DisplayName("Should export audit logs to CSV format")
    void shouldExportAuditLogsToCsv() {
        // Arrange
        LocalDateTime start = LocalDateTime.of(2025, 12, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 12, 2, 0, 0);
        when(auditService.getAuditLogsBetween(start, end)).thenReturn(sampleLogs);

        // Act
        String csv = auditExportService.exportToCsv(start, end);

        // Assert
        assertThat(csv).isNotNull();
        assertThat(csv).contains("ID,Timestamp,User ID,Event Type,Severity,Description,IP Address");
        assertThat(csv).contains("LOGIN_SUCCESS");
        assertThat(csv).contains("CHATBOT_CREATED");
        assertThat(csv).contains("192.168.1.100");
        assertThat(csv).contains("INFO");

        // Verify correct service call
        verify(auditService, times(1)).getAuditLogsBetween(start, end);
    }

    @Test
    @DisplayName("Should export audit logs to JSON format")
    void shouldExportAuditLogsToJson() {
        // Arrange
        LocalDateTime start = LocalDateTime.of(2025, 12, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 12, 2, 0, 0);
        when(auditService.getAuditLogsBetween(start, end)).thenReturn(sampleLogs);

        // Act
        String json = auditExportService.exportToJson(start, end);

        // Assert
        assertThat(json).isNotNull();
        assertThat(json).startsWith("[");
        assertThat(json).endsWith("]");
        assertThat(json).contains("\"eventType\":\"LOGIN_SUCCESS\"");
        assertThat(json).contains("\"eventType\":\"CHATBOT_CREATED\"");
        assertThat(json).contains("\"ipAddress\":\"192.168.1.100\"");
        assertThat(json).contains("\"severity\":\"INFO\"");

        verify(auditService, times(1)).getAuditLogsBetween(start, end);
    }

    @Test
    @DisplayName("Should export user-specific audit logs to CSV")
    void shouldExportUserSpecificLogsToCsv() {
        // Arrange
        when(auditService.getAuditLogsForUser(1L)).thenReturn(sampleLogs);

        // Act
        String csv = auditExportService.exportUserLogsToCsv(1L);

        // Assert
        assertThat(csv).isNotNull();
        assertThat(csv).contains("ID,Timestamp,User ID,Event Type,Severity,Description,IP Address");
        assertThat(csv).contains("LOGIN_SUCCESS");
        assertThat(csv).contains("CHATBOT_CREATED");

        verify(auditService, times(1)).getAuditLogsForUser(1L);
    }

    @Test
    @DisplayName("Should handle empty audit logs in CSV export")
    void shouldHandleEmptyLogsInCsvExport() {
        // Arrange
        LocalDateTime start = LocalDateTime.of(2025, 12, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 12, 2, 0, 0);
        when(auditService.getAuditLogsBetween(start, end)).thenReturn(Arrays.asList());

        // Act
        String csv = auditExportService.exportToCsv(start, end);

        // Assert
        assertThat(csv).isNotNull();
        assertThat(csv).contains("ID,Timestamp,User ID,Event Type,Severity,Description,IP Address");
        assertThat(csv.split("\n")).hasSize(1); // Only header line
    }

    @Test
    @DisplayName("Should handle empty audit logs in JSON export")
    void shouldHandleEmptyLogsInJsonExport() {
        // Arrange
        LocalDateTime start = LocalDateTime.of(2025, 12, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 12, 2, 0, 0);
        when(auditService.getAuditLogsBetween(start, end)).thenReturn(Arrays.asList());

        // Act
        String json = auditExportService.exportToJson(start, end);

        // Assert
        assertThat(json).isNotNull();
        assertThat(json).isEqualTo("[]");
    }

    @Test
    @DisplayName("Should properly escape CSV special characters")
    void shouldEscapeCsvSpecialCharacters() {
        // Arrange
        AuditLog logWithComma = TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.CHATBOT_UPDATED);
        logWithComma.setId(3L);
        logWithComma.setDescription("Updated bot, added new features");
        logWithComma.setTimestamp(LocalDateTime.of(2025, 12, 1, 12, 0));

        when(auditService.getAuditLogsForUser(1L)).thenReturn(Arrays.asList(logWithComma));

        // Act
        String csv = auditExportService.exportUserLogsToCsv(1L);

        // Assert
        assertThat(csv).contains("\"Updated bot, added new features\"");
    }

    @Test
    @DisplayName("Should include all required fields in CSV export")
    void shouldIncludeAllFieldsInCsvExport() {
        // Arrange
        when(auditService.getAuditLogsBetween(any(), any())).thenReturn(sampleLogs);

        // Act
        String csv = auditExportService.exportToCsv(
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now()
        );

        // Assert
        String[] lines = csv.split("\n");
        String[] headers = lines[0].split(",");

        assertThat(headers).contains("ID", "Timestamp", "User ID", "Event Type",
                                     "Severity", "Description", "IP Address");

        // Check that data rows have correct number of columns
        for (int i = 1; i < lines.length; i++) {
            String[] columns = lines[i].split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"); // Split by comma, respecting quotes
            assertThat(columns.length).isGreaterThanOrEqualTo(7);
        }
    }

    @Test
    @DisplayName("Should format timestamps correctly in CSV")
    void shouldFormatTimestampsInCsv() {
        // Arrange
        when(auditService.getAuditLogsForUser(1L)).thenReturn(sampleLogs);

        // Act
        String csv = auditExportService.exportUserLogsToCsv(1L);

        // Assert
        assertThat(csv).contains("2025-12-01");
        assertThat(csv).containsPattern("\\d{4}-\\d{2}-\\d{2}");
    }
}
