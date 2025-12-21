package com.prayer_chat.chatbot.repository;

import com.prayer_chat.chatbot.model.AuditLog;
import com.prayer_chat.chatbot.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for AuditLog entity
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find all audit logs for a specific user
     */
    Page<AuditLog> findByUser(User user, Pageable pageable);

    /**
     * Find all audit logs by user ID
     */
    Page<AuditLog> findByUserId(Long userId, Pageable pageable);

    /**
     * Find audit logs by event type
     */
    Page<AuditLog> findByEventType(AuditLog.EventType eventType, Pageable pageable);

    /**
     * Find audit logs by severity
     */
    Page<AuditLog> findBySeverity(AuditLog.Severity severity, Pageable pageable);

    /**
     * Find audit logs within a date range
     */
    Page<AuditLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * Find audit logs for a specific user within a date range
     */
    @Query("SELECT a FROM AuditLog a WHERE a.user.id = :userId AND a.createdAt BETWEEN :start AND :end")
    Page<AuditLog> findByUserIdAndDateRange(
        @Param("userId") Long userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end,
        Pageable pageable
    );

    /**
     * Find security-critical events
     */
    @Query("SELECT a FROM AuditLog a WHERE a.severity = 'CRITICAL' OR a.severity = 'ERROR' ORDER BY a.createdAt DESC")
    List<AuditLog> findCriticalEvents(Pageable pageable);

    /**
     * Find suspicious activities for fraud detection
     */
    @Query("SELECT a FROM AuditLog a WHERE a.eventType IN ('SECURITY_SUSPICIOUS_ACTIVITY', 'SECURITY_ACCESS_DENIED', 'AUTH_FAILED', 'SUBSCRIPTION_PAYMENT_FAILED') AND a.createdAt >= :since")
    List<AuditLog> findSuspiciousActivities(@Param("since") LocalDateTime since);

    /**
     * Count failed login attempts for a user within a time window
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.user.id = :userId AND a.eventType = 'AUTH_FAILED' AND a.createdAt >= :since")
    long countFailedLoginAttempts(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /**
     * Count payment failures for a user
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.user.id = :userId AND a.eventType = 'SUBSCRIPTION_PAYMENT_FAILED' AND a.createdAt >= :since")
    long countPaymentFailures(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /**
     * Find all audit logs for export (without pagination)
     */
    @Query("SELECT a FROM AuditLog a WHERE a.user.id = :userId AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    List<AuditLog> findAllForExport(
        @Param("userId") Long userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    /**
     * Delete old audit logs (for cleanup)
     */
    void deleteByCreatedAtBefore(LocalDateTime date);

    // Additional methods for tests (non-pageable versions)

    /**
     * Find all audit logs for a user ordered by timestamp descending
     */
    List<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Find audit logs by event type ordered by timestamp descending
     */
    List<AuditLog> findByEventTypeOrderByCreatedAtDesc(AuditLog.EventType eventType);

    /**
     * Find audit logs by severity ordered by timestamp descending
     */
    List<AuditLog> findBySeverityOrderByCreatedAtDesc(AuditLog.Severity severity);

    /**
     * Find audit logs within timestamp range ordered by timestamp descending
     */
    List<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    /**
     * Count events by user ID, event type after a specific timestamp
     */
    long countByUserIdAndEventTypeAndCreatedAtAfter(Long userId, AuditLog.EventType eventType, LocalDateTime timestamp);
}
