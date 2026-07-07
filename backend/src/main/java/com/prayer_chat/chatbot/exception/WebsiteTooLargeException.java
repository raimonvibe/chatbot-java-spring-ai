package com.prayer_chat.chatbot.exception;

import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.service.BillingModeService;

import java.util.Map;

/**
 * Thrown when a website exceeds the user's per-scan page limit.
 */
public class WebsiteTooLargeException extends RuntimeException {

    private final Map<String, Object> payload;
    private final boolean billingEnabled;

    public WebsiteTooLargeException(BillingModeService billingModeService,
                                    int estimatedPages,
                                    int maxPages,
                                    Subscription.SubscriptionPlan suggestedPlan,
                                    int suggestedMaxPages) {
        super("Website exceeds page limit");
        this.billingEnabled = billingModeService.isBillingEnabled();
        this.payload = billingModeService.websiteTooLargePayload(
            estimatedPages, maxPages, suggestedPlan, suggestedMaxPages);
    }

    public Map<String, Object> toPayload() {
        return payload;
    }

    public boolean isBillingEnabled() {
        return billingEnabled;
    }
}
