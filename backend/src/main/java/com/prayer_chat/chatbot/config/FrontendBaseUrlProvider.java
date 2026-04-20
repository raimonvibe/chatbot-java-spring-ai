package com.prayer_chat.chatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/**
 * Resolves the frontend base URL (scheme + host, no path) for OAuth redirects and API metadata.
 * <p>
 * Security: values come only from configuration ({@code app.frontend-url}, then {@code cors.allowed-origins}),
 * never from request headers, so redirects cannot be influenced by forged {@code Origin}, {@code Referer}, or {@code Host}.
 */
@Component
public class FrontendBaseUrlProvider {

    private static final Logger log = LoggerFactory.getLogger(FrontendBaseUrlProvider.class);

    private final String baseUrl;

    public FrontendBaseUrlProvider(
            @Value("${app.frontend-url}") String appFrontendUrl,
            @Value("${cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        this.baseUrl = resolve(appFrontendUrl, allowedOrigins);
        log.debug("Resolved frontend base URL for redirects/API: {}", baseUrl);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    static String resolve(String appFrontendUrl, String allowedOrigins) {
        if (appFrontendUrl != null) {
            String canonical = toCanonicalBaseUrl(appFrontendUrl);
            if (canonical != null) {
                return canonical;
            }
            if (!appFrontendUrl.trim().isEmpty()) {
                log.warn("Ignoring invalid app.frontend-url value: {}", appFrontendUrl);
            }
        }
        String fromCors = pickFromAllowedOrigins(allowedOrigins);
        if (fromCors != null && !fromCors.isEmpty()) {
            return fromCors;
        }
        log.warn("app.frontend-url is blank and no usable CORS origin; using http://localhost:3000");
        return "http://localhost:3000";
    }

    static String stripTrailingSlash(String url) {
        if (url == null || url.length() <= 1) {
            return url;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Picks a single origin from the comma-separated CORS list (same priority as legacy OAuth redirect logic).
     */
    static String pickFromAllowedOrigins(String allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.trim().isEmpty()) {
            return null;
        }
        String[] origins = allowedOrigins.split(",");
        String productionUrl = null;
        String testUrl = null;
        String firstNonLocalhost = null;
        String localhostUrl = null;

        for (String originConfig : origins) {
            String trimmed = originConfig.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            trimmed = trimmed.replace("https://*.", "https://");
            String canonical = toCanonicalBaseUrl(trimmed);
            if (canonical == null) {
                continue;
            }

            if (isLocalhost(canonical)) {
                if (localhostUrl == null) {
                    localhostUrl = canonical;
                }
                continue;
            }

            if (firstNonLocalhost == null) {
                firstNonLocalhost = canonical;
            }

            String host = URI.create(canonical).getHost();
            if ("www.prayer-chat.com".equalsIgnoreCase(host)) {
                productionUrl = canonical;
                break;
            }
            if ("prayer-chat.com".equalsIgnoreCase(host) && productionUrl == null) {
                productionUrl = canonical;
            }

            if (testUrl == null && host != null && host.toLowerCase(Locale.ROOT).endsWith(".vercel.app")) {
                testUrl = canonical;
            }
        }

        if (productionUrl == null && firstNonLocalhost != null) {
            String host = URI.create(firstNonLocalhost).getHost();
            if ("prayer-chat.com".equalsIgnoreCase(host) || "www.prayer-chat.com".equalsIgnoreCase(host)) {
                productionUrl = firstNonLocalhost;
            }
        }

        String selected = productionUrl != null ? productionUrl
                : (testUrl != null ? testUrl
                : (firstNonLocalhost != null ? firstNonLocalhost
                : localhostUrl));
        if (selected == null || selected.isEmpty()) {
            return null;
        }
        return stripTrailingSlash(selected);
    }

    private static boolean isLocalhost(String canonicalUrl) {
        String host = URI.create(canonicalUrl).getHost();
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
    }

    /**
     * Convert a raw URL to canonical base URL (scheme://host[:port]), or return null if invalid.
     */
    private static String toCanonicalBaseUrl(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || host.isBlank()) {
                return null;
            }
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return null;
            }
            if (uri.getUserInfo() != null && !uri.getUserInfo().isEmpty()) {
                return null;
            }
            String canonicalScheme = scheme.toLowerCase(Locale.ROOT);
            String canonicalHost = host.toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (port > 0) {
                return canonicalScheme + "://" + canonicalHost + ":" + port;
            }
            return canonicalScheme + "://" + canonicalHost;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
