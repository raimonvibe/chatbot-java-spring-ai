package com.prayer_chat.chatbot.security;

import com.prayer_chat.chatbot.service.SecurityAlertService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting Filter using Bucket4j
 * Prevents abuse by limiting requests per client
 * 
 * NOTE: This filter is DISABLED in test profile to allow E2E tests to make
 * many requests without hitting rate limits. Rate limiting is tested separately
 * in RateLimitingFilterTest (unit tests).
 */
@Component
@Profile("!test")  // Disable in test profile - E2E tests need unlimited requests
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    /** Hard cap to keep the bucket cache bounded; buckets refill within a minute so a reset is harmless. */
    private static final int MAX_CACHE_ENTRIES = 100_000;

    @Autowired(required = false)
    private SecurityAlertService securityAlertService;

    @Autowired
    private ClientIpResolver clientIpResolver;

    @Value("${cors.allowed-origins:http://localhost:3000,https://prayer-chat.com,https://www.prayer-chat.com}")
    private String allowedOrigins;

    // Rate limits per minute
    private static final int CHAT_LIMIT = 20; // Chat endpoints: 20 requests per minute
    private static final int API_LIMIT = 60;  // Other API endpoints: 60 requests per minute
    private static final int GENERAL_LIMIT = 100; // General endpoints: 100 requests per minute

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip rate limiting for OPTIONS requests (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = getClientIdentifier(request);
        String path = request.getRequestURI();

        // Determine rate limit based on endpoint
        int limit = determineRateLimit(path);

        Bucket bucket = resolveBucket(key, limit);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            logger.warn("Rate limit exceeded for client: {} on path: {}", key, path);
            if (securityAlertService != null) {
                securityAlertService.alertRateLimitViolation(key, path);
            }

            // Add CORS headers before sending error response.
            // SECURITY: credentials are only allowed for configured dashboard origins; any other
            // origin gets a non-credentialed header so cross-site pages can't read credentialed responses.
            String origin = request.getHeader("Origin");
            if (origin != null) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                if (isAllowedDashboardOrigin(origin)) {
                    response.setHeader("Access-Control-Allow-Credentials", "true");
                }
                response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS,PATCH");
                response.setHeader("Access-Control-Allow-Headers", "*");
            }

            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Too many requests. Please try again later.\"}");
        }
    }

    private boolean isAllowedDashboardOrigin(String origin) {
        if (allowedOrigins == null || allowedOrigins.isBlank()) return false;
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
        return origins.contains(origin);
    }

    /**
     * Determine rate limit based on endpoint path
     */
    private int determineRateLimit(String path) {
        if (path.startsWith("/api/chat/")) {
            return CHAT_LIMIT;
        } else if (path.startsWith("/api/")) {
            return API_LIMIT;
        } else {
            return GENERAL_LIMIT;
        }
    }

    /**
     * Resolve or create bucket for client.
     * The key includes the rate-limit tier so a loose-tier bucket (e.g. /health at 100/min)
     * is never reused for a strict-tier path (e.g. /api/chat at 20/min).
     */
    private Bucket resolveBucket(String key, int limit) {
        if (cache.size() > MAX_CACHE_ENTRIES) {
            logger.warn("Rate limit bucket cache exceeded {} entries; resetting", MAX_CACHE_ENTRIES);
            cache.clear();
        }
        return cache.computeIfAbsent("l" + limit + ":" + key, k -> createNewBucket(limit));
    }

    /**
     * Create new rate limit bucket
     */
    private Bucket createNewBucket(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.classic(
                requestsPerMinute,
                Refill.intervally(requestsPerMinute, Duration.ofMinutes(1))
        );
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Get client identifier.
     *
     * <p>SECURITY: limits are always keyed by client IP. Keying by unvalidated
     * Authorization / X-API-Key header values would let an attacker mint a fresh
     * bucket per request simply by rotating random header values.
     */
    private String getClientIdentifier(HttpServletRequest request) {
        return "ip:" + getClientIp(request);
    }

    /**
     * Get client IP address (handles proxies)
     */
    private String getClientIp(HttpServletRequest request) {
        if (clientIpResolver != null) {
            return clientIpResolver.resolveClientIp(request);
        }
        // Fallback (mainly for unit tests that construct the filter without Spring wiring)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

}
