package com.tjanabot.chatbot.service;

import com.tjanabot.chatbot.model.AuditLog;
import com.tjanabot.chatbot.model.User;
import com.tjanabot.chatbot.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for managing audit logs
 * Provides async logging to avoid performance impact on main operations
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Log an audit event asynchronously
     */
    @Async
    @Transactional
    public void log(AuditLog.EventType eventType, String action, User user) {
        AuditLog log = new AuditLog.Builder(eventType, action)
            .user(user)
            .build();

        enrichWithRequestInfo(log);
        auditLogRepository.save(log);
        logger.debug("Audit log created: {} - {}", eventType, action);
    }

    /**
     * Log an audit event with description
     */
    @Async
    @Transactional
    public void log(AuditLog.EventType eventType, String action, User user, String description) {
        AuditLog log = new AuditLog.Builder(eventType, action)
            .user(user)
            .description(description)
            .build();

        enrichWithRequestInfo(log);
        auditLogRepository.save(log);
        logger.debug("Audit log created: {} - {}", eventType, action);
    }

    /**
     * Log an audit event with full details
     */
    @Async
    @Transactional
    public void log(AuditLog.EventType eventType, AuditLog.Severity severity, String action,
                    User user, String description, String resourceType, String resourceId) {
        AuditLog log = new AuditLog.Builder(eventType, action)
            .user(user)
            .severity(severity)
            .description(description)
            .resource(resourceType, resourceId)
            .build();

        enrichWithRequestInfo(log);
        auditLogRepository.save(log);
        logger.debug("Audit log created: {} - {} ({})", eventType, action, severity);
    }

    /**
     * Log an audit event with metadata
     */
    @Async
    @Transactional
    public void log(AuditLog.EventType eventType, AuditLog.Severity severity, String action,
                    User user, String description, Map<String, String> metadata) {
        AuditLog log = new AuditLog.Builder(eventType, action)
            .user(user)
            .severity(severity)
            .description(description)
            .metadata(metadata)
            .build();

        enrichWithRequestInfo(log);
        auditLogRepository.save(log);
        logger.debug("Audit log created: {} - {} with metadata", eventType, action);
    }

    /**
     * Log a pre-built audit event
     */
    @Async
    @Transactional
    public void log(AuditLog auditLog) {
        enrichWithRequestInfo(auditLog);
        auditLogRepository.save(auditLog);
        logger.debug("Audit log created: {} - {}", auditLog.getEventType(), auditLog.getAction());
    }

    /**
     * Enrich audit log with HTTP request information
     */
    private void enrichWithRequestInfo(AuditLog log) {
        try {
            ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                log.setIpAddress(getClientIpAddress(request));
                log.setUserAgent(request.getHeader("User-Agent"));
            }
        } catch (Exception e) {
            // Ignore - request context not available (e.g., in async or scheduled tasks)
            logger.trace("Could not enrich audit log with request info: {}", e.getMessage());
        }
    }

    /**
     * Get client IP address from request, considering proxies
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * Get audit logs for a user
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getUserAuditLogs(Long userId, Pageable pageable) {
        return auditLogRepository.findByUserId(userId, pageable);
    }

    /**
     * Get audit logs within a date range
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByDateRange(Long userId, LocalDateTime start,
                                                   LocalDateTime end, Pageable pageable) {
        return auditLogRepository.findByUserIdAndDateRange(userId, start, end, pageable);
    }

    /**
     * Get all audit logs for export (no pagination)
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getAllAuditLogsForExport(Long userId, LocalDateTime start, LocalDateTime end) {
        return auditLogRepository.findAllForExport(userId, start, end);
    }

    /**
     * Get critical security events
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getCriticalEvents(Pageable pageable) {
        return auditLogRepository.findCriticalEvents(pageable);
    }

    /**
     * Detect suspicious activities for fraud detection
     */
    @Transactional(readOnly = true)
    public List<AuditLog> detectSuspiciousActivities(LocalDateTime since) {
        return auditLogRepository.findSuspiciousActivities(since);
    }

    /**
     * Check if user has too many failed login attempts
     */
    @Transactional(readOnly = true)
    public boolean hasTooManyFailedLogins(Long userId, int threshold, int minutesWindow) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutesWindow);
        long failedAttempts = auditLogRepository.countFailedLoginAttempts(userId, since);
        return failedAttempts >= threshold;
    }

    /**
     * Check if user has too many payment failures
     */
    @Transactional(readOnly = true)
    public boolean hasTooManyPaymentFailures(Long userId, int threshold, int daysWindow) {
        LocalDateTime since = LocalDateTime.now().minusDays(daysWindow);
        long paymentFailures = auditLogRepository.countPaymentFailures(userId, since);
        return paymentFailures >= threshold;
    }

    /**
     * Cleanup old audit logs (for compliance/storage management)
     */
    @Transactional
    public void cleanupOldLogs(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        auditLogRepository.deleteByCreatedAtBefore(cutoffDate);
        logger.info("Cleaned up audit logs older than {} days", daysToKeep);
    }
}
