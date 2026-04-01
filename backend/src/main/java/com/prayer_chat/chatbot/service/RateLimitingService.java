package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.config.PlanLimits;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.MessageRepository;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import com.prayer_chat.chatbot.repository.WebsiteScanAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;

/**
 * Service for enforcing usage-based rate limits on messages and website scans.
 * Limits are defined per plan in {@link PlanLimits} (monthly scan quota, daily scan cap, messages/day).
 */
@Service
public class RateLimitingService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingService.class);

    private final MessageRepository messageRepository;
    private final WebsiteScanAuditRepository websiteScanAuditRepository;
    private final AccessControlService accessControlService;
    private final SubscriptionRepository subscriptionRepository;

    @Autowired
    public RateLimitingService(MessageRepository messageRepository,
                               WebsiteScanAuditRepository websiteScanAuditRepository,
                               AccessControlService accessControlService,
                               SubscriptionRepository subscriptionRepository) {
        this.messageRepository = messageRepository;
        this.websiteScanAuditRepository = websiteScanAuditRepository;
        this.accessControlService = accessControlService;
        this.subscriptionRepository = subscriptionRepository;
    }

    private static Subscription.SubscriptionPlan planFor(User user, SubscriptionRepository subscriptionRepository) {
        Optional<Subscription> sub = subscriptionRepository.findByUserId(user.getId());
        if (sub.isEmpty() || !sub.get().isActive()) return Subscription.SubscriptionPlan.FREE;
        return sub.get().getPlan();
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
        Subscription.SubscriptionPlan plan = planFor(user, subscriptionRepository);
        int messageLimit = PlanLimits.messagesPerDay(plan);
        boolean isPreviewMode = accessControlService.isPreviewMode(user);

        Long messagesToday = messageRepository.countUserMessagesTodayByUserId(user.getId());
        if (messagesToday == null) messagesToday = 0L;

        boolean allowed = messagesToday < messageLimit;
        if (!allowed) {
            logger.warn("User {} attempted to send message but daily limit reached (current: {}, limit: {}, plan: {})",
                user.getId(), messagesToday, messageLimit, plan);
        }
        return new RateLimitResult(allowed, messageLimit, messagesToday.intValue(), isPreviewMode, "message");
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
        Subscription.SubscriptionPlan plan = planFor(user, subscriptionRepository);
        int dailyLimit = PlanLimits.dailyScanLimit(plan);
        int monthlyQuota = PlanLimits.monthlyScanQuota(plan);
        boolean isPreviewMode = accessControlService.isPreviewMode(user);

        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        Long scansInLastDay = websiteScanAuditRepository.countDistinctScanDatesByUserAndDateAfter(user.getId(), oneDayAgo);
        if (scansInLastDay == null) scansInLastDay = 0L;

        LocalDateTime startOfMonth = YearMonth.now().atDay(1).atStartOfDay();
        long scansThisMonth = websiteScanAuditRepository.countScansByUserAndScanDateAfter(user.getId(), startOfMonth);

        boolean overDaily = scansInLastDay >= dailyLimit;
        boolean overMonthly = scansThisMonth >= monthlyQuota;
        boolean allowed = !overDaily && !overMonthly;
        int effectiveLimit = overMonthly ? monthlyQuota : dailyLimit;
        int current = overMonthly ? (int) scansThisMonth : scansInLastDay.intValue();

        if (!allowed) {
            logger.warn("User {} attempted to scan but limit reached (daily: {}/{}, monthly: {}/{}, plan: {})",
                user.getId(), scansInLastDay, dailyLimit, scansThisMonth, monthlyQuota, plan);
        }
        return new RateLimitResult(allowed, overMonthly ? monthlyQuota : dailyLimit, current, isPreviewMode, "scan");
    }
    
    /**
     * Get the maximum number of messages allowed per day for a user
     */
    public int getMaxMessagesPerDay(User user) {
        return PlanLimits.messagesPerDay(planFor(user, subscriptionRepository));
    }

    /**
     * Get the maximum number of scans allowed per day for a user (plan-based).
     */
    public int getMaxScansPerDay(User user) {
        return PlanLimits.dailyScanLimit(planFor(user, subscriptionRepository));
    }

    /**
     * Get the monthly scan quota for a user (plan-based).
     */
    public int getMonthlyScanQuota(User user) {
        return PlanLimits.monthlyScanQuota(planFor(user, subscriptionRepository));
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

