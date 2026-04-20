package com.prayer_chat.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Controls whether the app should trust forwarded headers (X-Forwarded-For / X-Real-IP).
 *
 * Security: forwarded headers are only trustworthy when added/stripped by a proxy at the trust boundary.
 * This config lets us restrict that trust to known proxy IP ranges.
 */
@Validated
@ConfigurationProperties(prefix = "app.security.proxy")
public class ProxyHeaderProperties {

    /**
     * When true, the app will read forwarded headers to determine the real client IP, BUT ONLY
     * when the immediate sender (request.getRemoteAddr()) is in {@link #trustedProxies}.
     */
    private boolean trustForwardedHeaders = false;

    /**
     * List of trusted proxy IPs or CIDR blocks (e.g. "10.0.0.0/8", "192.168.0.0/16", "203.0.113.5/32").
     * If empty, forwarded headers are never trusted.
     */
    private List<String> trustedProxies = new ArrayList<>();

    public boolean isTrustForwardedHeaders() {
        return trustForwardedHeaders;
    }

    public void setTrustForwardedHeaders(boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        if (trustedProxies == null) {
            this.trustedProxies = new ArrayList<>();
            return;
        }
        // Env vars often provide a single comma-separated string; support both styles.
        if (trustedProxies.size() == 1 && trustedProxies.get(0) != null && trustedProxies.get(0).contains(",")) {
            String raw = trustedProxies.get(0);
            List<String> split = new ArrayList<>();
            for (String part : raw.split(",")) {
                if (part == null) continue;
                String t = part.trim();
                if (!t.isEmpty()) split.add(t);
            }
            this.trustedProxies = split;
            return;
        }
        this.trustedProxies = trustedProxies;
    }
}

