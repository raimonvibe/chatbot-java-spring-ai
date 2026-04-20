package com.prayer_chat.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cloudflare Turnstile settings for bot protection.
 *
 * When enabled, public widget chat requests must include a valid Turnstile token.
 */
@Validated
@ConfigurationProperties(prefix = "app.turnstile")
public class TurnstileProperties {

    /** Master switch. Keep false by default until frontend widget sends tokens. */
    private boolean enabled = false;

    /** Secret key used for server-side verification. Never expose to frontend. */
    private String secretKey = "";

    /** Site key used by the widget frontend. Safe to expose. */
    private String siteKey = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey != null ? secretKey : "";
    }

    public String getSiteKey() {
        return siteKey;
    }

    public void setSiteKey(String siteKey) {
        this.siteKey = siteKey != null ? siteKey : "";
    }
}

