package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Security event alerting - SECURITY_AUDIT_PLAN Phase 2.3.
 * Raises alerts for: failed login spikes, rate limit violations, payment failures, fraud risk.
 * Logs at ERROR and optionally sends to a webhook (Slack, PagerDuty, etc.) for integration
 * with Sentry, DataDog, or custom monitoring.
 */
@Service
public class SecurityAlertService {

    private static final Logger logger = LoggerFactory.getLogger(SecurityAlertService.class);

    @Value("${app.security.alert-webhook-url:}")
    private String webhookUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public enum AlertType {
        FAILED_LOGIN_SPIKE,
        RATE_LIMIT_VIOLATION,
        PAYMENT_FAILURE,
        FRAUD_RISK,
        SECURITY_EXCEPTION
    }

    /**
     * Raise a security alert. Always logged at ERROR; optionally sent to webhook if configured.
     */
    @Async
    public void alert(AlertType type, String message, Map<String, Object> context) {
        String sanitizedMessage = LogSanitizer.sanitize(message);
        logger.error("[SECURITY_ALERT] type={} message={} context={}", type, sanitizedMessage, sanitizeContext(context));

        if (webhookUrl != null && !webhookUrl.isBlank()) {
            sendToWebhook(type, sanitizedMessage, context);
        }
    }

    /**
     * Convenience: alert for failed login spike (e.g. 5+ in 30 min).
     */
    public void alertFailedLoginSpike(Long userId, long attemptCount, int windowMinutes) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("userId", userId);
        ctx.put("attemptCount", attemptCount);
        ctx.put("windowMinutes", windowMinutes);
        alert(AlertType.FAILED_LOGIN_SPIKE,
            String.format("Failed login spike: %d attempts in %d minutes for user %d", attemptCount, windowMinutes, userId),
            ctx);
    }

    /**
     * Convenience: alert for rate limit violation.
     */
    public void alertRateLimitViolation(String clientKey, String path) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("clientKey", clientKey != null ? clientKey.substring(0, Math.min(50, clientKey.length())) : "unknown");
        ctx.put("path", path);
        alert(AlertType.RATE_LIMIT_VIOLATION,
            String.format("Rate limit exceeded for client on path: %s", path),
            ctx);
    }

    /**
     * Convenience: alert for payment failure (e.g. Stripe invoice.payment_failed).
     */
    public void alertPaymentFailure(String reason, Long userId, String stripeEventId) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("userId", userId);
        ctx.put("stripeEventId", stripeEventId);
        alert(AlertType.PAYMENT_FAILURE,
            reason != null ? reason : "Payment failure reported",
            ctx);
    }

    /**
     * Convenience: alert for fraud / high risk.
     */
    public void alertFraudRisk(Long userId, String reason, int riskScore) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("userId", userId);
        ctx.put("riskScore", riskScore);
        alert(AlertType.FRAUD_RISK, reason != null ? reason : "Fraud risk detected", ctx);
    }

    private void sendToWebhook(AlertType type, String message, Map<String, Object> context) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("type", type.name());
            body.put("message", message);
            body.put("context", context);
            body.put("timestamp", Instant.now().toString());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForObject(webhookUrl, new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            logger.warn("Failed to send security alert to webhook: {}", e.getMessage());
        }
    }

    private static Map<String, Object> sanitizeContext(Map<String, Object> context) {
        if (context == null) return Map.of();
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, Object> e : context.entrySet()) {
            Object v = e.getValue();
            out.put(e.getKey(), v instanceof String ? LogSanitizer.sanitize((String) v) : v);
        }
        return out;
    }
}
