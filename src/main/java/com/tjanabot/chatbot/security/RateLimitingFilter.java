package com.tjanabot.chatbot.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting Filter using Bucket4j
 * Prevents abuse by limiting requests per client
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    // Rate limits per minute
    private static final int CHAT_LIMIT = 20; // Chat endpoints: 20 requests per minute
    private static final int API_LIMIT = 60;  // Other API endpoints: 60 requests per minute
    private static final int GENERAL_LIMIT = 100; // General endpoints: 100 requests per minute

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String key = getClientIdentifier(request);
        String path = request.getRequestURI();

        // Determine rate limit based on endpoint
        int limit = determineRateLimit(path);

        Bucket bucket = resolveBucket(key, limit);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            logger.warn("Rate limit exceeded for client: {} on path: {}", key, path);
            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Too many requests. Please try again later.\"}");
        }
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
     * Resolve or create bucket for client
     */
    private Bucket resolveBucket(String key, int limit) {
        return cache.computeIfAbsent(key, k -> createNewBucket(limit));
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
     * Get client identifier (IP address or API key)
     */
    private String getClientIdentifier(HttpServletRequest request) {
        // Use API key if provided
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isEmpty()) {
            return "api-key:" + apiKey;
        }

        // Use Authorization token if present
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return "token:" + authHeader.substring(7, Math.min(authHeader.length(), 20));
        }

        // Otherwise use IP address
        return "ip:" + getClientIp(request);
    }

    /**
     * Get client IP address (handles proxies)
     */
    private String getClientIp(HttpServletRequest request) {
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
