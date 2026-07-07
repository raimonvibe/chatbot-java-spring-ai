package com.prayer_chat.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Typed API response for subscription status endpoints.
 * Matches the frontend {@code SubscriptionStatusApi} contract.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubscriptionStatusResponse(
    Boolean billingEnabled,
    Boolean paymentActionsAvailable,
    Boolean hasSubscription,
    String status,
    String plan,
    Boolean isActive,
    Boolean canUseChatbot,
    LocalDateTime currentPeriodEnd,
    LocalDateTime canceledAt,
    Integer websiteScansMonthlyQuota,
    Integer websiteScansUsedThisMonth,
    Integer websiteScansDailyLimit,
    Integer websiteScansUsedRollingDay,
    Integer websiteScansRemaining,
    Boolean synced
) {}
