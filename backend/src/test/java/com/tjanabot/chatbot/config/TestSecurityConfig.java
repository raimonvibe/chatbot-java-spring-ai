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
 * Permits all requests during testing to focus on input validation rather than authorization
 */
@TestConfiguration
@EnableWebSecurity
@Profile("test")
@Order(1)
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/**")
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().permitAll()
            )
            // lgtm[java/spring-disabled-csrf-protection]
            // CSRF is intentionally disabled in test environment to simplify integration testing.
            // Production security config (SecurityConfig.java) has CSRF properly enabled.
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }
}
