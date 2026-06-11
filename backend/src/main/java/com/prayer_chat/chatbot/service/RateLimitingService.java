package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.config.PlanLimits;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.MessageRepository;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import com.prayer_chat.chatbot.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final BillingModeService billingModeService;

    @Autowired
    public RateLimitingService(MessageRepository messageRepository,
                               WebsiteScanAuditRepository websiteScanAuditRepository,
                               AccessControlService accessControlService,
                               SubscriptionRepository subscriptionRepository,
                               UserRepository userRepository,
                               BillingModeService billingModeService) {
        this.messageRepository = messageRepository;
        this.websiteScanAuditRepository = websiteScanAuditRepository;
        this.accessControlService = accessControlService;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.billingModeService = billingModeService;
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
    @Transactional
    public RateLimitResult checkMessageLimit(User user) {
        if (user == null || user.getId() == null) {
            return new RateLimitResult(false, 0, 0, false, "message", false);
        }
        userRepository.findByIdWithLock(user.getId())
            .orElseThrow(() -> new RuntimeException("User not found: " + user.getId()));
        Subscription.SubscriptionPlan plan = planFor(user, subscriptionRepository);
        int messageLimit = billingModeService.effectiveMessagesPerDay(plan);
        boolean isPreviewMode = accessControlService.isPreviewMode(user);

        Long messagesToday = messageRepository.countUserMessagesTodayByUserId(user.getId());
        if (messagesToday == null) messagesToday = 0L;

        boolean allowed = messagesToday < messageLimit;
        if (!allowed) {
            logger.warn("User {} attempted to send message but daily limit reached (current: {}, limit: {}, plan: {})",
                user.getId(), messagesToday, messageLimit, plan);
        }
        return new RateLimitResult(allowed, messageLimit, messagesToday.intValue(), isPreviewMode, "message",
            isPreviewMode && billingModeService.isBillingEnabled());
    }

    /**
     * Extra guardrail against multi-account abuse from the same end-user IP.
     * <p>
     * Enforced only when billing is disabled (free product mode).
     */
    @Transactional(readOnly = true)
    public IpMessageLimitResult checkIpMessageLimit(String clientIp) {
        int limit = billingModeService.effectiveMessagesPerIpDay();
        if (limit == Integer.MAX_VALUE) {
            return new IpMessageLimitResult(true, limit, 0);
        }
        if (clientIp == null || clientIp.isBlank()) {
            return new IpMessageLimitResult(true, limit, 0);
        }

        Long messagesToday = messageRepository.countUserMessagesTodayByUserIp(clientIp);
        if (messagesToday == null) messagesToday = 0L;

        boolean allowed = messagesToday < limit;
        return new IpMessageLimitResult(allowed, limit, messagesToday.intValue());
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
    @Transactional
    public RateLimitResult checkScanLimit(User user) {
        if (user == null || user.getId() == null) {
            return new RateLimitResult(false, 0, 0, false, "scan", false);
        }
        userRepository.findByIdWithLock(user.getId())
            .orElseThrow(() -> new RuntimeException("User not found: " + user.getId()));
        Subscription.SubscriptionPlan plan = planFor(user, subscriptionRepository);
        int dailyLimit = billingModeService.effectiveDailyScanLimit(plan);
        int monthlyQuota = billingModeService.effectiveMonthlyScanQuota(plan);
        boolean isPreviewMode = accessControlService.isPreviewMode(user);

        // Calendar-day window, consistent with the daily message limit (CAST(created_at AS DATE) = CURRENT_DATE).
        Long scansInLastDay = websiteScanAuditRepository.countScansTodayByUserId(user.getId());
        if (scansInLastDay == null) scansInLastDay = 0L;

        LocalDateTime startOfMonth = YearMonth.now().atDay(1).atStartOfDay();
        long scansThisMonth = websiteScanAuditRepository.countScansByUserAndScanDateAfter(user.getId(), startOfMonth);

        boolean overDaily = scansInLastDay >= dailyLimit;
        boolean overMonthly = scansThisMonth >= monthlyQuota;
        boolean allowed = !overDaily && !overMonthly;
        int current = overMonthly ? (int) scansThisMonth : scansInLastDay.intValue();

        if (!allowed) {
            logger.warn("User {} attempted to scan but limit reached (daily: {}/{}, monthly: {}/{}, plan: {})",
                user.getId(), scansInLastDay, dailyLimit, scansThisMonth, monthlyQuota, plan);
        }
        return new RateLimitResult(allowed, overMonthly ? monthlyQuota : dailyLimit, current, isPreviewMode, "scan",
            isPreviewMode && billingModeService.isBillingEnabled());
    }

    /**
     * Current website-scan usage for UI (e.g. delete-chatbot confirmation). Remaining = min of monthly and daily
     * headroom, matching {@link #checkScanLimit(User)}.
     */
    @Transactional(readOnly = true)
    public WebsiteScanQuotaSnapshot getWebsiteScanQuotaSnapshot(User user) {
        Subscription.SubscriptionPlan plan = planFor(user, subscriptionRepository);
        int dailyLimit = billingModeService.effectiveDailyScanLimit(plan);
        int monthlyQuota = billingModeService.effectiveMonthlyScanQuota(plan);

        // Calendar-day window, matching checkScanLimit.
        Long scansInLastDay = websiteScanAuditRepository.countScansTodayByUserId(user.getId());
        if (scansInLastDay == null) {
            scansInLastDay = 0L;
        }

        LocalDateTime startOfMonth = YearMonth.now().atDay(1).atStartOfDay();
        long scansThisMonth = websiteScanAuditRepository.countScansByUserAndScanDateAfter(user.getId(), startOfMonth);

        long remMonthly = Math.max(0L, (long) monthlyQuota - scansThisMonth);
        long remDaily = Math.max(0L, (long) dailyLimit - scansInLastDay);
        int remainingEffective = (int) Math.min(remMonthly, remDaily);

        return new WebsiteScanQuotaSnapshot(
            monthlyQuota,
            scansThisMonth,
            dailyLimit,
            scansInLastDay.intValue(),
            remainingEffective
        );
    }

    /** Website scan limits and usage for authenticated subscription/status API. */
    public record WebsiteScanQuotaSnapshot(
        int monthlyQuota,
        long usedThisMonth,
        int dailyLimit,
        int usedScansInRollingWindow,
        int remainingScansEffective
    ) {}

    /**
     * Get the maximum number of messages allowed per day for a user
     */
    public int getMaxMessagesPerDay(User user) {
        return billingModeService.effectiveMessagesPerDay(planFor(user, subscriptionRepository));
    }

    /**
     * Get the maximum number of scans allowed per day for a user (plan-based).
     */
    public int getMaxScansPerDay(User user) {
        return billingModeService.effectiveDailyScanLimit(planFor(user, subscriptionRepository));
    }

    /**
     * Get the monthly scan quota for a user (plan-based).
     */
    public int getMonthlyScanQuota(User user) {
        return billingModeService.effectiveMonthlyScanQuota(planFor(user, subscriptionRepository));
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
        /** True when UI/API may offer Stripe upgrade (preview-tier limit hit while billing is on). */
        private final boolean upgradeSuggested;

        public RateLimitResult(boolean allowed, int limit, int current, boolean isPreviewMode, String type,
                               boolean upgradeSuggested) {
            this.allowed = allowed;
            this.limit = limit;
            this.current = current;
            this.isPreviewMode = isPreviewMode;
            this.type = type;
            this.upgradeSuggested = upgradeSuggested;
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

        public boolean isUpgradeSuggested() {
            return upgradeSuggested;
        }

        public String getErrorMessage() {
            if ("message".equals(type)) {
                if (upgradeSuggested) {
                    return String.format(
                        "Daily message limit reached. Preview mode allows %d messages per day. Upgrade to continue.", limit);
                }
                return String.format("Daily message limit reached (%d messages per day). Please try again tomorrow.", limit);
            }
            if (upgradeSuggested) {
                return String.format(
                    "Website scan limit reached (monthly quota + daily cap). Upgrade to run more scans.", limit);
            }
            return "Website scan limit reached (monthly quota + daily cap). Please try again later.";
        }
    }

    /** Simple result type for end-user-IP throttles (not plan-based). */
    public static class IpMessageLimitResult {
        private final boolean allowed;
        private final int limit;
        private final int current;

        public IpMessageLimitResult(boolean allowed, int limit, int current) {
            this.allowed = allowed;
            this.limit = limit;
            this.current = current;
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

        public String getErrorMessage() {
            return String.format(
                "Daily message limit reached for this IP (%d messages/day). Please try again tomorrow.",
                limit
            );
        }
    }
}

