package com.prayer_chat.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Feature flag for Stripe checkout, billing portal, plan changes, and webhook-driven subscription updates.
 * <p>
 * When {@code enabled} is {@code false}, the app runs as a free product: payment endpoints return
 * {@code 403}, webhooks acknowledge but do not mutate state, and UI should hide upgrade flows
 * (see {@code NEXT_PUBLIC_BILLING_ENABLED} on the frontend). Stripe integration code remains in the
 * codebase for when you re-enable paid plans — set {@code app.billing.enabled=true} and configure
 * Stripe keys. Documented in {@code docs/FREE_PRODUCT_BILLING.md}.
 * </p>
 */
@Validated
@ConfigurationProperties(prefix = "app.billing")
public class BillingProperties {

    /**
     * Master switch for paid billing. Default {@code true} preserves backward compatibility for
     * existing deployments and tests; the main {@code application.yml} sets {@code false} for the
     * free-product default. Integration tests set {@code true} in {@code application-test.yml}.
     */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
