package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.model.AuditLog;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import com.prayer_chat.chatbot.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for detecting fraudulent activity and suspicious behavior
 */
@Service
public class FraudDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(FraudDetectionService.class);

    // Fraud detection thresholds
    private static final int MAX_FAILED_LOGINS = 5;
    private static final int FAILED_LOGIN_WINDOW_MINUTES = 30;

    private static final int MAX_PAYMENT_FAILURES = 3;
    private static final int PAYMENT_FAILURE_WINDOW_DAYS = 7;

    private static final int SUSPICIOUS_ACTIVITY_THRESHOLD = 10;
    private static final int SUSPICIOUS_ACTIVITY_WINDOW_HOURS = 1;

    @Autowired
    private AuditService auditService;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    /**
     * Analyze user activity for fraud indicators
     */
    @Transactional(readOnly = true)
    public FraudAnalysisResult analyzeUser(Long userId) {
        FraudAnalysisResult result = new FraudAnalysisResult();
        result.userId = userId;
        result.timestamp = LocalDateTime.now();

        // Check failed login attempts
        boolean tooManyFailedLogins = auditService.hasTooManyFailedLogins(
            userId, MAX_FAILED_LOGINS, FAILED_LOGIN_WINDOW_MINUTES
        );
        result.tooManyFailedLogins = tooManyFailedLogins;

        // Check payment failures
        boolean tooManyPaymentFailures = auditService.hasTooManyPaymentFailures(
            userId, MAX_PAYMENT_FAILURES, PAYMENT_FAILURE_WINDOW_DAYS
        );
        result.tooManyPaymentFailures = tooManyPaymentFailures;

        // Calculate risk score
        int riskScore = 0;
        if (tooManyFailedLogins) riskScore += 30;
        if (tooManyPaymentFailures) riskScore += 40;

        result.riskScore = riskScore;
        result.riskLevel = calculateRiskLevel(riskScore);

        return result;
    }

    /**
     * Detect suspicious payment patterns
     */
    @Transactional(readOnly = true)
    public boolean isSuspiciousPaymentPattern(User user) {
        // Check for multiple payment failures
        boolean hasPaymentFailures = auditService.hasTooManyPaymentFailures(
            user.getId(), MAX_PAYMENT_FAILURES, PAYMENT_FAILURE_WINDOW_DAYS
        );

        if (hasPaymentFailures) {
            logger.warn("Suspicious payment pattern detected for user: {}",
                LogSanitizer.sanitize(user.getEmail()));

            // Log the suspicious activity
            auditService.log(
                AuditLog.EventType.SECURITY_SUSPICIOUS_ACTIVITY,
                AuditLog.Severity.WARNING,
                "Suspicious payment pattern detected",
                user,
                String.format("User has %d+ payment failures in %d days",
                    MAX_PAYMENT_FAILURES, PAYMENT_FAILURE_WINDOW_DAYS),
                null
            );

            return true;
        }

        return false;
    }

    /**
     * Detect account takeover attempts
     */
    @Transactional
    public boolean isAccountTakeoverAttempt(User user, String currentIp, String currentUserAgent) {
        // Get recent successful logins
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<AuditLog> recentLogins = auditService.detectSuspiciousActivities(since);

        // Check for IP address changes or user agent changes (simplified fraud detection)
        // In production, this would be more sophisticated with geolocation, device fingerprinting, etc.

        boolean suspicious = auditService.hasTooManyFailedLogins(
            user.getId(), MAX_FAILED_LOGINS, FAILED_LOGIN_WINDOW_MINUTES
        );

        if (suspicious) {
            logger.warn("Potential account takeover attempt for user: {}",
                LogSanitizer.sanitize(user.getEmail()));

            Map<String, String> metadata = new HashMap<>();
            metadata.put("ip_address", currentIp);
            metadata.put("user_agent", currentUserAgent);

            auditService.log(
                AuditLog.EventType.SECURITY_SUSPICIOUS_ACTIVITY,
                AuditLog.Severity.CRITICAL,
                "Potential account takeover attempt",
                user,
                "Multiple failed login attempts detected",
                metadata
            );

            return true;
        }

        return false;
    }

    /**
     * Monitor subscription abuse (e.g., frequent cancellations and re-subscriptions)
     */
    @Transactional(readOnly = true)
    public boolean isSubscriptionAbuse(User user) {
        // Check subscription history
        // This is a simplified check - in production, you'd track subscription changes over time

        var subscription = subscriptionRepository.findByUserId(user.getId());
        if (subscription.isEmpty()) {
            return false;
        }

        Subscription sub = subscription.get();

        // Check if subscription was canceled and recreated recently
        if (sub.getCanceledAt() != null &&
            sub.getCanceledAt().isAfter(LocalDateTime.now().minusDays(7)) &&
            sub.getStatus() == Subscription.SubscriptionStatus.ACTIVE) {

            logger.warn("Potential subscription abuse detected for user: {}",
                LogSanitizer.sanitize(user.getEmail()));

            auditService.log(
                AuditLog.EventType.SECURITY_SUSPICIOUS_ACTIVITY,
                AuditLog.Severity.WARNING,
                "Potential subscription abuse detected",
                user,
                "Subscription canceled and recreated within 7 days",
                null
            );

            return true;
        }

        return false;
    }

    /**
     * Detect unusual usage patterns
     */
    @Transactional
    public void monitorUsagePatterns(User user, String action, int count) {
        // Monitor for unusual spikes in activity
        // This is a placeholder for more sophisticated usage monitoring

        if (count > SUSPICIOUS_ACTIVITY_THRESHOLD) {
            logger.warn("Unusual usage pattern detected for user {}: {} {} times",
                LogSanitizer.sanitize(user.getEmail()), action, count);

            Map<String, String> metadata = new HashMap<>();
            metadata.put("action", action);
            metadata.put("count", String.valueOf(count));
            metadata.put("threshold", String.valueOf(SUSPICIOUS_ACTIVITY_THRESHOLD));

            auditService.log(
                AuditLog.EventType.SECURITY_SUSPICIOUS_ACTIVITY,
                AuditLog.Severity.WARNING,
                "Unusual usage pattern detected",
                user,
                String.format("User performed '%s' %d times in a short period", action, count),
                metadata
            );
        }
    }

    /**
     * Calculate risk level from risk score
     */
    private RiskLevel calculateRiskLevel(int riskScore) {
        if (riskScore >= 70) return RiskLevel.CRITICAL;
        if (riskScore >= 50) return RiskLevel.HIGH;
        if (riskScore >= 30) return RiskLevel.MEDIUM;
        if (riskScore > 0) return RiskLevel.LOW;
        return RiskLevel.NONE;
    }

    /**
     * Fraud analysis result
     */
    public static class FraudAnalysisResult {
        public Long userId;
        public LocalDateTime timestamp;
        public boolean tooManyFailedLogins;
        public boolean tooManyPaymentFailures;
        public int riskScore;
        public RiskLevel riskLevel;
        public String recommendation;

        public boolean isSuspicious() {
            return riskScore > 0;
        }
    }

    /**
     * Risk levels
     */
    public enum RiskLevel {
        NONE,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
