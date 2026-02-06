package com.prayer_chat.chatbot.config;

import com.prayer_chat.chatbot.model.Subscription;

import java.math.BigDecimal;

/**
 * Usage-based limits per subscription plan.
 * Aligned with actual costs: website scans (embeddings) are the main cost driver.
 *
 * Use cases:
 * - FREE: Try before buy; minimal scans and pages to control cost.
 * - BASIC: Small sites, few chatbots; light monthly usage.
 * - PRO: Medium sites, agencies; moderate scans and page limits.
 * - ENTERPRISE: Large sites (e.g. big docs), high volume; high limits.
 */
public final class PlanLimits {

    private PlanLimits() {}

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

    /** Maximum pages per single website scan (large sites = high embedding cost). */
    public static int maxPagesPerScan(Subscription.SubscriptionPlan plan) {
        if (plan == null) return 50;
        return switch (plan) {
            case FREE -> 50;
            case BASIC -> 500;
            case PRO -> 2_000;
            case ENTERPRISE -> 10_000;
        };
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

    /** Maximum chatbots per account. */
    public static int maxChatbots(Subscription.SubscriptionPlan plan) {
        if (plan == null) return 1;
        return switch (plan) {
            case FREE -> 1;
            case BASIC -> 3;
            case PRO -> 10;
            case ENTERPRISE -> 50;
        };
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
