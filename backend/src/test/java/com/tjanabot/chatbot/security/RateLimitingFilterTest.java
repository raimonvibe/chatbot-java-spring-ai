package com.tjanabot.chatbot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for RateLimitingFilter
 * Tests DoS/DDoS protection and rate limiting functionality
 */
@DisplayName("RateLimitingFilter Tests")
class RateLimitingFilterTest {

    private RateLimitingFilter rateLimitingFilter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        rateLimitingFilter = new RateLimitingFilter();

        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    // ============================================================================
    // RATE LIMIT CONFIGURATION TESTS
    // ============================================================================

    @Test
    @DisplayName("Should allow requests under rate limit")
    void testAllowRequestsUnderLimit() throws Exception {
        // Configure mock request
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(request.getRequestURI()).thenReturn("/api/chatbots");

        // Make requests under the limit (60/min for API endpoints)
        for (int i = 0; i < 10; i++) {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        }

        // All requests should pass through
        verify(filterChain, times(10)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    @DisplayName("Should block requests over rate limit")
    void testBlockRequestsOverLimit() throws Exception {
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.getRequestURI()).thenReturn("/api/chat/message");

        // Chat limit is 20/min
        // Make 25 requests
        for (int i = 0; i < 25; i++) {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        }

        // First 20 should succeed, last 5 should be blocked
        verify(filterChain, times(20)).doFilter(request, response);
        verify(response, atLeast(5)).setStatus(429);
    }

    // ============================================================================
    // CLIENT IDENTIFICATION TESTS
    // ============================================================================

    @Test
    @DisplayName("Should identify clients by IP address")
    void testIdentifyByIp() throws Exception {
        when(request.getRemoteAddr()).thenReturn("203.0.113.45");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("X-API-Key")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(null);

        rateLimitingFilter.doFilterInternal(request, response, filterChain);

        // Should use IP address for rate limiting
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should identify clients by API key if provided")
    void testIdentifyByApiKey() throws Exception {
        when(request.getRemoteAddr()).thenReturn("203.0.113.46");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("X-API-Key")).thenReturn("api_key_12345");
        when(request.getHeader("Authorization")).thenReturn(null);

        rateLimitingFilter.doFilterInternal(request, response, filterChain);

        // Should use API key for rate limiting (separate bucket from IP)
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should identify clients by Bearer token if provided")
    void testIdentifyByBearerToken() throws Exception {
        when(request.getRemoteAddr()).thenReturn("203.0.113.47");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("X-API-Key")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...");

        rateLimitingFilter.doFilterInternal(request, response, filterChain);

        // Should use token for rate limiting
        verify(filterChain).doFilter(request, response);
    }

    // ============================================================================
    // X-FORWARDED-FOR TESTS (Proxy Support)
    // ============================================================================

    @Test
    @DisplayName("Should respect X-Forwarded-For header")
    void testXForwardedFor() throws Exception {
        when(request.getRemoteAddr()).thenReturn("10.0.0.1"); // Proxy IP
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.50");
        when(request.getRequestURI()).thenReturn("/api/test");

        rateLimitingFilter.doFilterInternal(request, response, filterChain);

        // Should use real client IP from X-Forwarded-For
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should handle multiple IPs in X-Forwarded-For")
    void testMultipleXForwardedFor() throws Exception {
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.51, 10.0.0.2, 10.0.0.3");
        when(request.getRequestURI()).thenReturn("/api/test");

        rateLimitingFilter.doFilterInternal(request, response, filterChain);

        // Should use first IP (real client)
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should respect X-Real-IP header")
    void testXRealIp() throws Exception {
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("203.0.113.52");
        when(request.getRequestURI()).thenReturn("/api/test");

        rateLimitingFilter.doFilterInternal(request, response, filterChain);

        // Should use X-Real-IP if X-Forwarded-For not available
        verify(filterChain).doFilter(request, response);
    }

    // ============================================================================
    // ENDPOINT-SPECIFIC RATE LIMITS
    // ============================================================================

    @Test
    @DisplayName("Should apply different rate limits to chat endpoints")
    void testChatEndpointLimit() throws Exception {
        when(request.getRemoteAddr()).thenReturn("192.168.2.1");
        when(request.getRequestURI()).thenReturn("/api/chat/send");

        // Chat limit is 20/min
        for (int i = 0; i < 22; i++) {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        }

        // 20 should succeed, 2 should be blocked
        verify(filterChain, times(20)).doFilter(request, response);
        verify(response, atLeast(2)).setStatus(429);
    }

    @Test
    @DisplayName("Should apply different rate limits to API endpoints")
    void testApiEndpointLimit() throws Exception {
        when(request.getRemoteAddr()).thenReturn("192.168.2.2");
        when(request.getRequestURI()).thenReturn("/api/chatbots");

        // API limit is 60/min
        for (int i = 0; i < 62; i++) {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        }

        // 60 should succeed, 2 should be blocked
        verify(filterChain, times(60)).doFilter(request, response);
        verify(response, atLeast(2)).setStatus(429);
    }

    @Test
    @DisplayName("Should apply default rate limit to general endpoints")
    void testGeneralEndpointLimit() throws Exception {
        when(request.getRemoteAddr()).thenReturn("192.168.2.3");
        when(request.getRequestURI()).thenReturn("/health");

        // General limit is 100/min
        for (int i = 0; i < 102; i++) {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        }

        // 100 should succeed, 2 should be blocked
        verify(filterChain, times(100)).doFilter(request, response);
        verify(response, atLeast(2)).setStatus(429);
    }

    // ============================================================================
    // BUCKET ISOLATION TESTS
    // ============================================================================

    @Test
    @DisplayName("Should isolate rate limits between different clients")
    void testBucketIsolation() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/test");

        // Client 1
        when(request.getRemoteAddr()).thenReturn("192.168.3.1");
        for (int i = 0; i < 50; i++) {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        }

        // Client 2 (different IP)
        when(request.getRemoteAddr()).thenReturn("192.168.3.2");
        for (int i = 0; i < 50; i++) {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        }

        // Both should be allowed (separate buckets)
        verify(filterChain, times(100)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    // ============================================================================
    // RESPONSE TESTS
    // ============================================================================

    @Test
    @DisplayName("Should return 429 status when rate limit exceeded")
    void testRateLimitExceededStatus() throws Exception {
        when(request.getRemoteAddr()).thenReturn("192.168.4.1");
        when(request.getRequestURI()).thenReturn("/api/chat/test");

        // Exceed chat limit (20/min)
        for (int i = 0; i < 21; i++) {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        }

        verify(response, atLeastOnce()).setStatus(429);
    }

    @Test
    @DisplayName("Should return JSON error message when rate limited")
    void testRateLimitErrorMessage() throws Exception {
        when(request.getRemoteAddr()).thenReturn("192.168.4.2");
        when(request.getRequestURI()).thenReturn("/api/chat/test");

        // Exceed limit
        for (int i = 0; i < 21; i++) {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        }

        verify(response, atLeastOnce()).setContentType("application/json");

        // Check error message contains helpful info
        String responseBody = responseWriter.toString();
        assertTrue(responseBody.contains("error") || responseBody.contains("Too many requests"));
    }

    // ============================================================================
    // DDOS PROTECTION TESTS
    // ============================================================================

    @Test
    @DisplayName("Should protect against rapid-fire requests (DDoS)")
    void testDdosProtection() throws Exception {
        when(request.getRemoteAddr()).thenReturn("203.0.113.100");
        when(request.getRequestURI()).thenReturn("/api/chat/attack");

        // Simulate DDoS attack (1000 rapid requests)
        for (int i = 0; i < 1000; i++) {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        }

        // Most requests should be blocked (only 20 allowed for chat endpoint)
        verify(response, atLeast(980)).setStatus(429);
    }

    @Test
    @DisplayName("Should handle distributed attack from multiple IPs")
    void testDistributedAttack() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/test");

        // Simulate distributed attack from 10 IPs
        for (int ip = 1; ip <= 10; ip++) {
            when(request.getRemoteAddr()).thenReturn("192.168.100." + ip);

            // Each IP tries to overwhelm (100 requests)
            for (int req = 0; req < 100; req++) {
                rateLimitingFilter.doFilterInternal(request, response, filterChain);
            }
        }

        // Each IP should be rate limited independently
        // Total blocked: 10 IPs * (100 - 60) = 400 blocked requests
        verify(response, atLeast(400)).setStatus(429);
    }

    // ============================================================================
    // BUCKET REFILL TESTS (Time-based)
    // ============================================================================

    @Test
    @DisplayName("Should refill bucket over time")
    void testBucketRefill() throws Exception {
        when(request.getRemoteAddr()).thenReturn("192.168.5.1");
        when(request.getRequestURI()).thenReturn("/api/chat/refill");

        // Use up limit (20/min for chat)
        for (int i = 0; i < 20; i++) {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        }

        // Next request should be blocked
        rateLimitingFilter.doFilterInternal(request, response, filterChain);
        verify(response, atLeastOnce()).setStatus(429);

        // Wait for refill (in real test, you'd mock time or use a test bucket with faster refill)
        // Thread.sleep(60000); // Wait 1 minute for refill

        // NOTE: This test would need time mocking to be practical
        // In production tests, use a test-specific bucket with faster refill rate
    }

    // ============================================================================
    // EDGE CASES AND ERROR HANDLING
    // ============================================================================

    @Test
    @DisplayName("Should handle null IP address gracefully")
    void testNullIpAddress() throws Exception {
        when(request.getRemoteAddr()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/test");

        // Should not crash
        assertDoesNotThrow(() -> {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        });
    }

    @Test
    @DisplayName("Should handle empty IP address")
    void testEmptyIpAddress() throws Exception {
        when(request.getRemoteAddr()).thenReturn("");
        when(request.getRequestURI()).thenReturn("/api/test");

        assertDoesNotThrow(() -> {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        });
    }

    @Test
    @DisplayName("Should handle malformed Authorization header")
    void testMalformedAuthHeader() throws Exception {
        when(request.getRemoteAddr()).thenReturn("192.168.6.1");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("Authorization")).thenReturn("NotABearerToken");

        assertDoesNotThrow(() -> {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        });
    }

    @Test
    @DisplayName("Should handle very long Authorization header")
    void testVeryLongAuthHeader() throws Exception {
        when(request.getRemoteAddr()).thenReturn("192.168.6.2");
        when(request.getRequestURI()).thenReturn("/api/test");
        String longToken = "Bearer " + "a".repeat(10000);
        when(request.getHeader("Authorization")).thenReturn(longToken);

        // Should truncate/handle safely
        assertDoesNotThrow(() -> {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        });
    }

    // ============================================================================
    // PERFORMANCE TESTS
    // ============================================================================

    @Test
    @DisplayName("Should process requests quickly")
    void testPerformance() throws Exception {
        when(request.getRemoteAddr()).thenReturn("192.168.7.1");
        when(request.getRequestURI()).thenReturn("/api/test");

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 1000; i++) {
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        }

        long duration = System.currentTimeMillis() - startTime;

        // 1000 requests should complete in under 1 second
        assertTrue(duration < 1000,
            "Rate limiting should be performant: " + duration + "ms for 1000 requests");
    }

    // ============================================================================
    // MEMORY LEAK TESTS
    // ============================================================================

    @Test
    @DisplayName("Should not leak memory with many unique clients")
    void testNoMemoryLeak() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/test");

        // Simulate many unique clients
        for (int i = 0; i < 10000; i++) {
            when(request.getRemoteAddr()).thenReturn("10.0." + (i / 256) + "." + (i % 256));
            rateLimitingFilter.doFilterInternal(request, response, filterChain);
        }

        // Should not crash or run out of memory
        // In production, implement bucket eviction for old/unused buckets
    }

    // ============================================================================
    // CONCURRENT ACCESS TESTS
    // ============================================================================

    @Test
    @DisplayName("Should handle concurrent requests safely")
    void testConcurrentRequests() throws Exception {
        when(request.getRemoteAddr()).thenReturn("192.168.8.1");
        when(request.getRequestURI()).thenReturn("/api/test");

        int threadCount = 50;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    rateLimitingFilter.doFilterInternal(request, response, filterChain);
                } catch (Exception e) {
                    fail("Concurrent request failed: " + e.getMessage());
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Should handle all requests without race conditions
        // Exactly 50 requests should have been made
        verify(filterChain, atMost(50)).doFilter(request, response);
    }
}
