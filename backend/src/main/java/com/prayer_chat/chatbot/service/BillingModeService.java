package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.config.BillingProperties;
import com.prayer_chat.chatbot.config.PlanLimits;
import com.prayer_chat.chatbot.model.Subscription;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central place for "free product" vs "billing on" behavior: cost caps, quotas, and user-facing payloads.
 * Payment services ({@link com.prayer_chat.chatbot.service.StripeService}) stay registered; this service
 * only gates product behavior when {@link BillingProperties#isEnabled()} is false.
 */
@Service
public class BillingModeService {

    private final BillingProperties billingProperties;

    /** Monthly AI/crawl cost ceiling when billing is off (protects against runaway API bills). */
    private static final BigDecimal FREE_PRODUCT_MONTHLY_COST_CAP_USD = new BigDecimal("2500.00");
    /**
     * Free-product caps (Stripe billing disabled).
     * These are server-enforced via {@code RateLimitingService} and must match what you display in the pricing UI.
     */
    private static final int FREE_PRODUCT_MESSAGES_PER_DAY = 30;
    /**
     * Additional guardrail to reduce multi-account abuse from the same IP/network.
     * This is only enforced when billing is disabled (Stripe off / preview/free product).
     */
    private static final int FREE_PRODUCT_MESSAGES_PER_IP_PER_DAY = 60;
    private static final int FREE_PRODUCT_MONTHLY_SCAN_QUOTA = 3;
    private static final int FREE_PRODUCT_DAILY_SCAN_LIMIT = 3;

    public BillingModeService(BillingProperties billingProperties) {
        this.billingProperties = billingProperties;
    }

    public boolean isBillingEnabled() {
        return billingProperties.isEnabled();
    }

    public BigDecimal effectiveMonthlyCostCapUsd(Subscription.SubscriptionPlan plan) {
        if (!isBillingEnabled()) {
            return FREE_PRODUCT_MONTHLY_COST_CAP_USD;
        }
        return PlanLimits.monthlyCostCapUsd(plan);
    }

    public int effectiveMonthlyScanQuota(Subscription.SubscriptionPlan plan) {
        if (!isBillingEnabled()) {
            return FREE_PRODUCT_MONTHLY_SCAN_QUOTA;
        }
        return PlanLimits.monthlyScanQuota(plan);
    }

    public int effectiveMessagesPerDay(Subscription.SubscriptionPlan plan) {
        if (!isBillingEnabled()) {
            return FREE_PRODUCT_MESSAGES_PER_DAY;
        }
        return PlanLimits.messagesPerDay(plan);
    }

    /**
     * End-user-IP message cap (free product only).
     * When billing is enabled we rely on user-level quotas plus stronger infrastructure limits.
     */
    public int effectiveMessagesPerIpDay() {
        if (!isBillingEnabled()) {
            return FREE_PRODUCT_MESSAGES_PER_IP_PER_DAY;
        }
        return Integer.MAX_VALUE;
    }

    public int effectiveDailyScanLimit(Subscription.SubscriptionPlan plan) {
        if (!isBillingEnabled()) {
            return FREE_PRODUCT_DAILY_SCAN_LIMIT;
        }
        return PlanLimits.dailyScanLimit(plan);
    }

    /**
     * Whether API responses should suggest upgrading (Stripe checkout). False when billing is disabled.
     */
    public boolean shouldSuggestPaidUpgrade() {
        return isBillingEnabled();
    }

    /**
     * Standard JSON body when a website exceeds the per-scan page limit.
     */
    public Map<String, Object> websiteTooLargePayload(
            int estimatedPages,
            int maxPagesForUser,
            Subscription.SubscriptionPlan suggestedPlan,
            int suggestedMaxPages) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("estimatedPages", estimatedPages);
        body.put("maxPages", maxPagesForUser);

        if (!isBillingEnabled()) {
            body.put("error",
                "This site looks larger than we can scan in one run (about "
                    + estimatedPages
                    + " pages detected; limit is "
                    + maxPagesForUser
                    + " pages). Try a smaller site, a subdomain, or a section with fewer pages.");
            body.put("message",
                "We limit scan size so the service stays fast and reliable for everyone. If you believe this is wrong, try again or use a more specific URL.");
            body.put("upgradeRequired", false);
            return body;
        }

        body.put("error",
            "Website too large for your plan. Your plan allows up to "
                + maxPagesForUser
                + " pages. Upgrade to "
                + suggestedPlan
                + " for sites up to "
                + suggestedMaxPages
                + " pages.");
        body.put("suggestedPlan", suggestedPlan.name());
        body.put("suggestedMaxPages", suggestedMaxPages);
        body.put("upgradeRequired", true);
        body.put("message",
            "We'd love to help you share your message more widely! Upgrade to scan websites with more pages.");
        return body;
    }
}
