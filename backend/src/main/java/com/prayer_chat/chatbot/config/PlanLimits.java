package com.prayer_chat.chatbot.config;

import com.prayer_chat.chatbot.model.Subscription;

import java.math.BigDecimal;

/**
 * Usage-based limits per subscription plan.
 * One chatbot per user; plan tier is defined by website size (max pages per scan).
 * These page tiers are the single source of truth — backend and frontend should reference them.
 *
 * <h3>Standard page tiers (cost-sustainable; FREE tier raised for current free product)</h3>
 * <pre>
 * Plan        | Max pages/scan | Est. scan cost (1×) | Monthly cost cap
 * ------------|----------------|---------------------|------------------
 * FREE        | 500            | ~$0.15              | $5
 * BASIC       | 500            | ~$0.15              | $15
 * PRO         | 2,000          | ~$0.60              | $50
 * ENTERPRISE  | 10,000         | ~$3.00              | $200
 * </pre>
 * When {@code app.billing.enabled=false}, all users effectively run under generous free-product caps
 * (see {@link com.prayer_chat.chatbot.service.BillingModeService}); paid tier constants remain for
 * re-enabling Stripe. One full scan at plan max stays well under the monthly cap when billing is on.
 */
public final class PlanLimits {

    private PlanLimits() {}

    /** Max pages per scan for FREE — current product default (was 50 when FREE was strictly preview-only). */
    public static final int FREE_MAX_PAGES = 500;
    /** Next tier above FREE (501+ pages) when billing is enabled; aligns with historical PRO-sized sites. */
    public static final int BASIC_MAX_PAGES = 2_000;
    public static final int PRO_MAX_PAGES = 2_000;
    public static final int ENTERPRISE_MAX_PAGES = 10_000;

    /** Maximum website scans per month for this plan. */
    public static int monthlyScanQuota(Subscription.SubscriptionPlan plan) {
        if (plan == null) return 1;
        return switch (plan) {
            case FREE -> 1;
            case BASIC -> 5;
            case PRO -> 20;
            case ENTERPRISE -> 100;
        };
    }

    /** Maximum pages per single website scan (plan tier = website size). Uses standard constants. */
    public static int maxPagesPerScan(Subscription.SubscriptionPlan plan) {
        if (plan == null) return FREE_MAX_PAGES;
        return switch (plan) {
            case FREE -> FREE_MAX_PAGES;
            case BASIC -> BASIC_MAX_PAGES;
            case PRO -> PRO_MAX_PAGES;
            case ENTERPRISE -> ENTERPRISE_MAX_PAGES;
        };
    }

    /**
     * Minimum plan required for a website of the given page count.
     * Use when showing upgrade path (e.g. "Your site has X pages → upgrade to Pro").
     * Negative or zero page count is treated as FREE (no upgrade needed).
     */
    public static Subscription.SubscriptionPlan minimumPlanForPages(int estimatedPages) {
        if (estimatedPages <= 0) return Subscription.SubscriptionPlan.FREE;
        if (estimatedPages <= FREE_MAX_PAGES) return Subscription.SubscriptionPlan.FREE;
        if (estimatedPages <= BASIC_MAX_PAGES) return Subscription.SubscriptionPlan.BASIC;
        if (estimatedPages <= PRO_MAX_PAGES) return Subscription.SubscriptionPlan.PRO;
        return Subscription.SubscriptionPlan.ENTERPRISE;
    }

    /** Monthly cost cap in USD (embedding + scan cost); null = no cap. */
    public static BigDecimal monthlyCostCapUsd(Subscription.SubscriptionPlan plan) {
        if (plan == null) return new BigDecimal("5.00");
        return switch (plan) {
            case FREE -> new BigDecimal("5.00");
            case BASIC -> new BigDecimal("15.00");
            case PRO -> new BigDecimal("50.00");
            case ENTERPRISE -> new BigDecimal("200.00");
        };
    }

    /** Maximum chatbots per account (one per user; plan determines website page limit). */
    public static int maxChatbots(Subscription.SubscriptionPlan plan) {
        return 1;
    }

    /** Maximum chat messages per day. */
    public static int messagesPerDay(Subscription.SubscriptionPlan plan) {
        if (plan == null) return 10;
        return switch (plan) {
            case FREE -> 10;
            case BASIC -> 100;
            case PRO -> 500;
            case ENTERPRISE -> 2_000;
        };
    }

    /** Daily scan limit (burst protection; monthly quota is the main limit). */
    public static int dailyScanLimit(Subscription.SubscriptionPlan plan) {
        if (plan == null) return 1;
        return switch (plan) {
            case FREE -> 1;
            case BASIC -> 3;
            case PRO -> 10;
            case ENTERPRISE -> 30;
        };
    }
}
