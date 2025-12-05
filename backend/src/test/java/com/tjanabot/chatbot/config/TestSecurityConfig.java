package com.tjanabot.chatbot.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test Security Configuration
 *
 * This configuration is ONLY active in test profile (@Profile("test")) and is designed
 * to simplify integration testing by disabling security features that would otherwise
 * require complex test setup.
 *
 * SECURITY JUSTIFICATION:
 * - CSRF is disabled in tests because:
 *   1. Tests focus on business logic and input validation, not CSRF protection
 *   2. Production uses JWT-based stateless authentication (no session cookies)
 *   3. CSRF protection is properly configured in production SecurityConfig.java
 *   4. This config is NEVER loaded in production (test profile only)
 *
 * See TESTING_TODO.md task #4 for plan to add dedicated CSRF protection tests.
 */
@TestConfiguration
@EnableWebSecurity
@Profile("test")
@Order(1)
public class TestSecurityConfig {

    /**
     * Test-only security filter chain.
     *
     * IMPORTANT: This configuration is NOT used in production.
     * Production security is configured in SecurityConfig.java
     *
     * Suppression rationale:
     * - CodeQL Alert: java/spring-disabled-csrf-protection
     * - Justification: Test environment only, production has proper security
     * - Risk: None (test profile only, never runs in production)
     */
    @Bean
    @SuppressWarnings("lgtm[java/spring-disabled-csrf-protection]")
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/**")
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().permitAll()
            )
            // Suppression: lgtm[java/spring-disabled-csrf-protection]
            // CSRF is intentionally disabled in TEST environment only.
            // This is safe because:
            // 1. This code ONLY runs when spring.profiles.active=test
            // 2. Production uses SecurityConfig.java with proper CSRF configuration
            // 3. The app uses JWT tokens (stateless) which don't require CSRF for APIs
            // 4. OAuth2 endpoints that need CSRF are properly protected in production
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }
}
