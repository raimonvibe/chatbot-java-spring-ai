package com.tjanabot.chatbot.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Entity representing an audit log entry for security and compliance tracking
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_event_type", columnList = "event_type"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_severity", columnList = "severity")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String resourceType;

    @Column(length = 100)
    private String resourceId;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 255)
    private String userAgent;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "audit_log_metadata", joinColumns = @JoinColumn(name = "audit_log_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value", columnDefinition = "TEXT")
    private Map<String, String> metadata = new HashMap<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // Event types for different actions
    public enum EventType {
        // Authentication events
        AUTH_LOGIN,
        AUTH_LOGOUT,
        AUTH_FAILED,
        AUTH_OAUTH_SUCCESS,

        // Subscription events
        SUBSCRIPTION_CREATED,
        SUBSCRIPTION_UPDATED,
        SUBSCRIPTION_CANCELED,
        SUBSCRIPTION_PAYMENT_SUCCESS,
        SUBSCRIPTION_PAYMENT_FAILED,
        SUBSCRIPTION_UPGRADED,
        SUBSCRIPTION_DOWNGRADED,

        // Chatbot events
        CHATBOT_CREATED,
        CHATBOT_UPDATED,
        CHATBOT_DELETED,
        CHATBOT_ACCESSED,

        // Chat events
        CHAT_MESSAGE_SENT,
        CHAT_SESSION_STARTED,

        // Security events
        SECURITY_ACCESS_DENIED,
        SECURITY_RATE_LIMIT_HIT,
        SECURITY_SUSPICIOUS_ACTIVITY,
        SECURITY_DATA_EXPORT,

        // Payment events
        PAYMENT_METHOD_ADDED,
        PAYMENT_METHOD_REMOVED,
        PAYMENT_REFUND_ISSUED,

        // Admin events
        ADMIN_ACTION,

        // System events
        SYSTEM_ERROR
    }

    // Severity levels
    public enum Severity {
        INFO,      // Normal operations
        WARNING,   // Potential issues
        ERROR,     // Errors that occurred
        CRITICAL   // Security-critical events
    }

    // Constructors
    public AuditLog() {
        this.createdAt = LocalDateTime.now();
    }

    public AuditLog(User user, EventType eventType, Severity severity, String action, String description) {
        this.user = user;
        this.eventType = eventType;
        this.severity = severity;
        this.action = action;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    // Builder pattern for easier creation
    public static class Builder {
        private final AuditLog log;

        public Builder(EventType eventType, String action) {
            this.log = new AuditLog();
            this.log.eventType = eventType;
            this.log.action = action;
            this.log.severity = Severity.INFO; // Default
            this.log.createdAt = LocalDateTime.now();
        }

        public Builder user(User user) {
            this.log.user = user;
            return this;
        }

        public Builder severity(Severity severity) {
            this.log.severity = severity;
            return this;
        }

        public Builder description(String description) {
            this.log.description = description;
            return this;
        }

        public Builder resource(String resourceType, String resourceId) {
            this.log.resourceType = resourceType;
            this.log.resourceId = resourceId;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.log.ipAddress = ipAddress;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.log.userAgent = userAgent;
            return this;
        }

        public Builder metadata(String key, String value) {
            this.log.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.log.metadata.putAll(metadata);
            return this;
        }

        public AuditLog build() {
            return this.log;
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
