package com.tjanabot.chatbot.integration.repository;

import com.tjanabot.chatbot.helpers.TestDataBuilder;
import com.tjanabot.chatbot.model.AuditLog;
import com.tjanabot.chatbot.model.User;
import com.tjanabot.chatbot.repository.AuditLogRepository;
import com.tjanabot.chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("AuditLogRepository Integration Tests")
class AuditLogRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();

        testUser = TestDataBuilder.createTestUser();
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("Should save and retrieve audit log")
    void shouldSaveAndRetrieveAuditLog() {
        // Arrange
        AuditLog log = TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.LOGIN_SUCCESS);

        // Act
        AuditLog saved = auditLogRepository.save(log);

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(saved.getEventType()).isEqualTo(AuditLog.EventType.LOGIN_SUCCESS);
    }

    @Test
    @DisplayName("Should find audit logs by user ID ordered by timestamp desc")
    void shouldFindByUserIdOrderByCreatedAtDesc() {
        // Arrange
        AuditLog log1 = TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.LOGIN_SUCCESS);
        log1.setTimestamp(LocalDateTime.now().minusHours(2));

        AuditLog log2 = TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.CHATBOT_CREATED);
        log2.setTimestamp(LocalDateTime.now().minusHours(1));

        auditLogRepository.save(log1);
        auditLogRepository.save(log2);

        // Act
        List<AuditLog> logs = auditLogRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId());

        // Assert
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getEventType()).isEqualTo(AuditLog.EventType.CHATBOT_CREATED); // Most recent first
        assertThat(logs.get(1).getEventType()).isEqualTo(AuditLog.EventType.LOGIN_SUCCESS);
    }

    @Test
    @DisplayName("Should find audit logs by event type")
    void shouldFindByEventType() {
        // Arrange
        auditLogRepository.save(TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.LOGIN_SUCCESS));
        auditLogRepository.save(TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.LOGIN_SUCCESS));
        auditLogRepository.save(TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.LOGIN_FAILURE));

        // Act
        List<AuditLog> successLogs = auditLogRepository.findByEventTypeOrderByCreatedAtDesc(
            AuditLog.EventType.LOGIN_SUCCESS
        );

        // Assert
        assertThat(successLogs).hasSize(2);
        assertThat(successLogs).allMatch(log -> log.getEventType() == AuditLog.EventType.LOGIN_SUCCESS);
    }

    @Test
    @DisplayName("Should find audit logs by severity")
    void shouldFindBySeverity() {
        // Arrange
        auditLogRepository.save(TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.LOGIN_SUCCESS,
            AuditLog.Severity.INFO));
        auditLogRepository.save(TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.SECURITY_ALERT,
            AuditLog.Severity.CRITICAL));
        auditLogRepository.save(TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.SUSPICIOUS_ACTIVITY,
            AuditLog.Severity.CRITICAL));

        // Act
        List<AuditLog> criticalLogs = auditLogRepository.findBySeverityOrderByCreatedAtDesc(
            AuditLog.Severity.CRITICAL
        );

        // Assert
        assertThat(criticalLogs).hasSize(2);
        assertThat(criticalLogs).allMatch(log -> log.getSeverity() == AuditLog.Severity.CRITICAL);
    }

    @Test
    @DisplayName("Should find audit logs within date range")
    void shouldFindWithinDateRange() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekAgo = now.minusDays(7);
        LocalDateTime twoWeeksAgo = now.minusDays(14);

        AuditLog recentLog = TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.LOGIN_SUCCESS);
        recentLog.setTimestamp(now.minusDays(3));

        AuditLog oldLog = TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.LOGIN_SUCCESS);
        oldLog.setTimestamp(twoWeeksAgo.minusDays(1));

        auditLogRepository.save(recentLog);
        auditLogRepository.save(oldLog);

        // Act
        List<AuditLog> recentLogs = auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
            weekAgo, now
        );

        // Assert
        assertThat(recentLogs).hasSize(1);
        assertThat(recentLogs.get(0).getTimestamp()).isAfter(weekAgo);
    }

    @Test
    @DisplayName("Should count events by user ID and event type after timestamp")
    void shouldCountEventsByUserAndTypeAfterTimestamp() {
        // Arrange
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);

        AuditLog recentFailure1 = TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.LOGIN_FAILURE);
        recentFailure1.setTimestamp(LocalDateTime.now().minusMinutes(30));

        AuditLog recentFailure2 = TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.LOGIN_FAILURE);
        recentFailure2.setTimestamp(LocalDateTime.now().minusMinutes(15));

        AuditLog oldFailure = TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.LOGIN_FAILURE);
        oldFailure.setTimestamp(LocalDateTime.now().minusHours(2));

        auditLogRepository.save(recentFailure1);
        auditLogRepository.save(recentFailure2);
        auditLogRepository.save(oldFailure);

        // Act
        long count = auditLogRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
            testUser.getId(),
            AuditLog.EventType.LOGIN_FAILURE,
            cutoff
        );

        // Assert
        assertThat(count).isEqualTo(2); // Only recent failures within the hour
    }

    @Test
    @DisplayName("Should handle audit logs without user (system events)")
    void shouldHandleAuditLogsWithoutUser() {
        // Arrange
        AuditLog systemLog = new AuditLog.Builder(AuditLog.EventType.SYSTEM_EVENT, "System started")
            .severity(AuditLog.Severity.INFO)
            .description("Application started successfully")
            .build();

        // Act
        AuditLog saved = auditLogRepository.save(systemLog);

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUser()).isNull();
        assertThat(saved.getEventType()).isEqualTo(AuditLog.EventType.SYSTEM_EVENT);
    }

    @Test
    @DisplayName("Should store IP address in audit log")
    void shouldStoreIpAddress() {
        // Arrange
        AuditLog log = TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.LOGIN_SUCCESS);
        log.setIpAddress("192.168.1.100");

        // Act
        AuditLog saved = auditLogRepository.save(log);

        // Assert
        assertThat(saved.getIpAddress()).isEqualTo("192.168.1.100");
    }

    @Test
    @DisplayName("Should store additional metadata as JSON")
    void shouldStoreMetadataAsJson() {
        // Arrange
        AuditLog log = TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.PAYMENT_SUCCESS);
        log.setMetadata("{\"amount\": 4.98, \"currency\": \"USD\"}");

        // Act
        AuditLog saved = auditLogRepository.save(log);

        // Assert
        assertThat(saved.getMetadata()).isEqualTo("{\"amount\": 4.98, \"currency\": \"USD\"}");
    }

    @Test
    @DisplayName("Should handle all event types")
    void shouldHandleAllEventTypes() {
        // Arrange & Act
        for (AuditLog.EventType eventType : AuditLog.EventType.values()) {
            AuditLog log = new AuditLog.Builder(eventType, "Test action")
                .user(testUser)
                .severity(AuditLog.Severity.INFO)
                .description("Testing " + eventType.name())
                .build();
            auditLogRepository.save(log);
        }

        // Assert
        List<AuditLog> allLogs = auditLogRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId());
        assertThat(allLogs).hasSizeGreaterThanOrEqualTo(AuditLog.EventType.values().length);
    }

    @Test
    @DisplayName("Should handle all severity levels")
    void shouldHandleAllSeverityLevels() {
        // Arrange & Act
        for (AuditLog.Severity severity : AuditLog.Severity.values()) {
            AuditLog log = new AuditLog.Builder(AuditLog.EventType.SYSTEM_EVENT, "Test")
                .user(testUser)
                .severity(severity)
                .description("Testing " + severity.name())
                .build();
            auditLogRepository.save(log);
        }

        // Assert
        List<AuditLog> allLogs = auditLogRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId());
        assertThat(allLogs).hasSizeGreaterThanOrEqualTo(AuditLog.Severity.values().length);
    }

    @Test
    @DisplayName("Should delete old audit logs")
    void shouldDeleteOldAuditLogs() {
        // Arrange
        AuditLog oldLog = TestDataBuilder.createAuditLog(testUser, AuditLog.EventType.LOGIN_SUCCESS);
        oldLog.setTimestamp(LocalDateTime.now().minusMonths(13)); // > 1 year old
        oldLog = auditLogRepository.save(oldLog);

        // Act
        auditLogRepository.deleteById(oldLog.getId());

        // Assert
        List<AuditLog> remainingLogs = auditLogRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId());
        assertThat(remainingLogs).isEmpty();
    }
}
