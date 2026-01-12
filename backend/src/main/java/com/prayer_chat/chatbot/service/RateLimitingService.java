package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.MessageRepository;
import com.prayer_chat.chatbot.repository.WebsiteScanAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for enforcing rate limits on messages and website scans
 *
 * Limits:
 * - Preview mode: 10 messages/day, 15 scans/day (INCREASED FOR TESTING)
 * - Paid mode: Unlimited messages, 15 scans/day
 */
@Service
public class RateLimitingService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingService.class);

    // Rate limits
    private static final int PREVIEW_MESSAGE_LIMIT = 10; // messages per day
    private static final int PREVIEW_SCAN_LIMIT = 15; // INCREASED FROM 1 FOR TESTING - scans per day
    private static final int PAID_SCAN_LIMIT = 15; // scans per day
    private static final int PAID_MESSAGE_LIMIT = Integer.MAX_VALUE; // unlimited
    
    private final MessageRepository messageRepository;
    private final WebsiteScanAuditRepository websiteScanAuditRepository;
    private final AccessControlService accessControlService;
    
    @Autowired
    public RateLimitingService(MessageRepository messageRepository,
                               WebsiteScanAuditRepository websiteScanAuditRepository,
                               AccessControlService accessControlService) {
        this.messageRepository = messageRepository;
        this.websiteScanAuditRepository = websiteScanAuditRepository;
        this.accessControlService = accessControlService;
    }
    
    /**
     * Check if user can send a message (rate limit check)
     * 
     * SECURITY NOTE: This method is not transactional. For high-concurrency scenarios,
     * consider adding pessimistic locking or using a distributed rate limiter (e.g., Redis).
     * Current implementation may allow slight overage under race conditions, but prevents
     * significant abuse.
     * 
     * @param user The user attempting to send a message
     * @return RateLimitResult with allowed status and details
     */
    @Transactional(readOnly = true)
    public RateLimitResult checkMessageLimit(User user) {
        boolean isPreviewMode = accessControlService.isPreviewMode(user);
        int messageLimit = isPreviewMode ? PREVIEW_MESSAGE_LIMIT : PAID_MESSAGE_LIMIT;
        
        // Count messages sent today
        Long messagesToday = messageRepository.countUserMessagesTodayByUserId(user.getId());
        if (messagesToday == null) {
            messagesToday = 0L;
        }
        
        boolean allowed = messagesToday < messageLimit;
        
        if (!allowed) {
            logger.warn("User {} attempted to send message but daily limit reached (current: {}, limit: {}, preview: {})", 
                user.getId(), messagesToday, messageLimit, isPreviewMode);
        }
        
        return new RateLimitResult(
            allowed,
            messageLimit,
            messagesToday.intValue(),
            isPreviewMode,
            "message"
        );
    }
    
    /**
     * Check if user can scan a website (rate limit check)
     * 
     * SECURITY NOTE: This method is not transactional. For high-concurrency scenarios,
     * consider adding pessimistic locking or using a distributed rate limiter (e.g., Redis).
     * Current implementation may allow slight overage under race conditions, but prevents
     * significant abuse.
     * 
     * @param user The user attempting to scan a website
     * @return RateLimitResult with allowed status and details
     */
    @Transactional(readOnly = true)
    public RateLimitResult checkScanLimit(User user) {
        boolean isPreviewMode = accessControlService.isPreviewMode(user);
        int scanLimit = isPreviewMode ? PREVIEW_SCAN_LIMIT : PAID_SCAN_LIMIT;
        
        // Count scans in the last 24 hours
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        Long scansInLastDay = websiteScanAuditRepository.countDistinctScanDatesByUserAndDateAfter(user.getId(), oneDayAgo);
        if (scansInLastDay == null) {
            scansInLastDay = 0L;
        }
        
        boolean allowed = scansInLastDay < scanLimit;
        
        if (!allowed) {
            logger.warn("User {} attempted to scan website but daily limit reached (current: {}, limit: {}, preview: {})", 
                user.getId(), scansInLastDay, scanLimit, isPreviewMode);
        }
        
        return new RateLimitResult(
            allowed,
            scanLimit,
            scansInLastDay.intValue(),
            isPreviewMode,
            "scan"
        );
    }
    
    /**
     * Get the maximum number of messages allowed per day for a user
     */
    public int getMaxMessagesPerDay(User user) {
        boolean isPreviewMode = accessControlService.isPreviewMode(user);
        return isPreviewMode ? PREVIEW_MESSAGE_LIMIT : PAID_MESSAGE_LIMIT;
    }
    
    /**
     * Get the maximum number of scans allowed per day for a user
     */
    public int getMaxScansPerDay(User user) {
        boolean isPreviewMode = accessControlService.isPreviewMode(user);
        return isPreviewMode ? PREVIEW_SCAN_LIMIT : PAID_SCAN_LIMIT;
    }
    
    /**
     * Result of a rate limit check
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final int limit;
        private final int current;
        private final boolean isPreviewMode;
        private final String type; // "message" or "scan"
        
        public RateLimitResult(boolean allowed, int limit, int current, boolean isPreviewMode, String type) {
            this.allowed = allowed;
            this.limit = limit;
            this.current = current;
            this.isPreviewMode = isPreviewMode;
            this.type = type;
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public int getLimit() {
            return limit;
        }
        
        public int getCurrent() {
            return current;
        }
        
        public boolean isPreviewMode() {
            return isPreviewMode;
        }
        
        public String getType() {
            return type;
        }
        
        public String getErrorMessage() {
            if (type.equals("message")) {
                if (isPreviewMode) {
                    return String.format("Daily message limit reached. Preview mode allows %d messages per day. Upgrade to continue.", limit);
                } else {
                    return String.format("Daily message limit reached (%d messages). Please try again tomorrow.", limit);
                }
            } else { // scan
                if (isPreviewMode) {
                    return String.format("Daily scan limit reached. Preview mode allows %d scan per day. Upgrade to continue.", limit);
                } else {
                    return String.format("Daily scan limit reached (%d scans per day). Please try again tomorrow.", limit);
                }
            }
        }
    }
}

