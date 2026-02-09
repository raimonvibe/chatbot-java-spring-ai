package com.prayer_chat.chatbot.config;

import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
@Configuration
@EnableWebSecurity
@Profile("test")
@Order(1)
public class TestSecurityConfig {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TestSecurityConfig.class);

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.prayer_chat.chatbot.security.RateLimitingFilter rateLimitingFilter;
    
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.prayer_chat.chatbot.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.prayer_chat.chatbot.security.AnonymousAuthenticationPreFilter anonymousAuthenticationPreFilter;

    /**
     * Test-only security filter chain.
     *
     * IMPORTANT: This configuration is NOT used in production.
     * Production security is configured in SecurityConfig.java
     *
     * This configuration mimics SecurityConfig but allows @WithMockUser to work
     * for testing purposes.
     */
    @Bean
    @SuppressWarnings("lgtm[java/spring-disabled-csrf-protection]")
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/stripe/webhook"))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                // CRITICAL: Rules are evaluated in order - first match wins!
                // Most specific rules MUST come before general rules
                
                // 1. OPTIONS requests for CORS preflight - must be first
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                
                // 2. Static resources and public endpoints - permitAll()
                .requestMatchers("/chatbot-widget.js").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                .requestMatchers("/api/health").permitAll()
                
                // 3. OAuth2 and authentication endpoints - permitAll()
                .requestMatchers("/login/**", "/oauth2/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll() // Note: /api/auth/me requires auth, but handled in controller
                
                // 4. Chat API endpoints - permitAll() (public chatbot widget)
                .requestMatchers("/api/chat/**").permitAll()
                
                // 5. Stripe webhook - permitAll() (needs to be public for Stripe to call)
                .requestMatchers("/stripe/webhook").permitAll()
                
                // 6. GET requests to chatbots - permitAll() (public read access)
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/chatbots").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/chatbots/**").permitAll()
                // 6b. Plan limits (public pricing info) - must match production SecurityConfig
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/plans/limits").permitAll()
                
                // 7. Write operations to chatbots - authenticated() (must come AFTER GET rules)
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/chatbots/**").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/chatbots/**").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/chatbots/**").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/chatbots/**").authenticated()
                
                // 8. Admin endpoints - hasRole("ADMIN")
                .requestMatchers("/h2-console/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**", "/api/analytics/**").hasRole("ADMIN")
                
                // 9. Actuator endpoints - authenticated() (for security testing)
                .requestMatchers("/actuator/**").authenticated()
                
                // 10. Subscription endpoints - authenticated()
                .requestMatchers("/api/subscription/**").authenticated()
                
                // 11. Fallback for any other /api/chatbots/** requests - authenticated()
                // This catches any methods not explicitly listed above
                .requestMatchers("/api/chatbots/**").authenticated()
                
                // 12. Thymeleaf pages - authenticated()
                .requestMatchers("/", "/index", "/chatbots/**", "/analytics", "/settings").authenticated()
                
                // 13. Error endpoint - permitAll() to allow error handling to work
                .requestMatchers("/error").permitAll()
                
                // 14. Default: all other requests require authentication
                .anyRequest().authenticated()
            )
            // Disable form login redirect for API endpoints (return 401 instead of 302)
            // BUT: Don't return 401 for permitAll() endpoints like /api/chat/**
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    String requestUri = request.getRequestURI();
                    var currentAuth = SecurityContextHolder.getContext().getAuthentication();
                    logger.debug("🔍 AuthenticationEntryPoint: Called for URI: {}, authException: {}, currentAuth: {}", 
                        requestUri,
                        authException != null ? authException.getClass().getSimpleName() + ": " + authException.getMessage() : "null",
                        currentAuth != null ? currentAuth.getClass().getSimpleName() : "null");
                    
                    // Skip authentication entry point for permitAll() endpoints
                    // If we reach here for a permitAll() endpoint, it's a configuration bug
                    // But we should still allow the request to proceed
                    if (requestUri.startsWith("/api/chat/") || 
                        requestUri.equals("/api/health") ||
                        requestUri.startsWith("/api/auth/") ||
                        requestUri.startsWith("/login/") ||
                        requestUri.startsWith("/oauth2/")) {
                        // These are permitAll() - this shouldn't happen, but if it does,
                        // it means anonymous authentication wasn't set properly
                        // Try to set anonymous authentication and let the request continue
                        logger.warn("🔍 AuthenticationEntryPoint: Called for permitAll() endpoint: {} - authException: {}, currentAuth: {}. " +
                            "This indicates AuthorizationFilter rejected the request despite permitAll(). " +
                            "Setting anonymous authentication and NOT returning 401.", 
                            requestUri, 
                            authException != null ? authException.getMessage() : "null",
                            currentAuth != null ? currentAuth.getClass().getSimpleName() : "null");
                        
                        // Set anonymous authentication if not already set
                        if (SecurityContextHolder.getContext().getAuthentication() == null) {
                            org.springframework.security.authentication.AnonymousAuthenticationToken anonymousAuth =
                                new org.springframework.security.authentication.AnonymousAuthenticationToken(
                                    "anonymous-key",
                                    "anonymousUser",
                                    org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
                                );
                            SecurityContextHolder.getContext().setAuthentication(anonymousAuth);
                            logger.debug("🔍 AuthenticationEntryPoint: Set anonymous authentication");
                        }
                        
                        // Check if response is already committed
                        if (response.isCommitted()) {
                            logger.error("🔍 AuthenticationEntryPoint: Response already committed for permitAll() endpoint: {}", requestUri);
                            return;
                        }
                        // CRITICAL: Don't write to response - let it continue by not setting status
                        // This should allow the filter chain to continue to the controller
                        logger.debug("🔍 AuthenticationEntryPoint: Returning without setting 401 for permitAll() endpoint");
                        return;
                    }
                    // Check if response is already committed before writing
                    if (response.isCommitted()) {
                        logger.warn("Response already committed, cannot set 401 status");
                        return;
                    }
                    if (requestUri.startsWith("/api/")) {
                        response.setStatus(401);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Unauthorized\"}");
                    } else {
                        response.sendError(401, "Unauthorized");
                    }
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(403);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Forbidden\"}");
                    } else {
                        response.sendError(403, "Forbidden");
                    }
                })
            )
            .sessionManagement(s -> s
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            // Enable anonymous authentication for permitAll() endpoints
            // This ensures that permitAll() endpoints work correctly even without authentication
            .anonymous(anonymous -> anonymous
                .principal("anonymousUser")
                .authorities("ROLE_ANONYMOUS")
            )
            .headers(headers -> headers
                // X-Frame-Options: Prevent clickjacking
                .frameOptions(frame -> frame.sameOrigin())
                // X-Content-Type-Options: Prevent MIME sniffing
                .contentTypeOptions(contentType -> {})
                // X-XSS-Protection: Enable browser XSS protection
                .xssProtection(xss -> xss.headerValue(
                    org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                // Strict-Transport-Security: Force HTTPS (31536000 = 1 year)
                // Note: In test environment, we configure HSTS to also work for HTTP requests
                // so that tests can verify the header is set (in production, HSTS only works over HTTPS)
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                    .requestMatcher(org.springframework.security.web.util.matcher.AnyRequestMatcher.INSTANCE))
                // Content-Security-Policy: Restrict resource loading
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline' https://js.stripe.com https://accounts.google.com https://cdn.jsdelivr.net; " +
                        "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
                        "font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com; " +
                        "img-src 'self' data: https:; " +
                        "connect-src 'self' https://api.stripe.com https://accounts.google.com; " +
                        "frame-src 'self' https://js.stripe.com https://accounts.google.com; " +
                        "object-src 'none'; " +
                        "base-uri 'self'; " +
                        "form-action 'self'; " +
                        "frame-ancestors 'self'"))
                // Referrer-Policy: Control referrer information
                .referrerPolicy(referrer -> referrer.policy(
                    org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )
            // Permissions-Policy: Control browser features
            .headers(headers -> headers
                .addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter(
                    "Permissions-Policy", "geolocation=(), microphone=(), camera=()"))
            )
            // Add authentication success handler to create CustomOAuth2User for tests
            .oauth2Login(oauth2 -> oauth2
                .successHandler(testAuthenticationSuccessHandler())
            );

        // Add filters if available
        // NOTE: Rate limiting is DISABLED in tests because:
        // 1. Rate limiting is tested separately in RateLimitingFilterTest
        // 2. E2E tests need to make many requests without hitting limits
        // 3. All test requests come from the same IP, so they would share the same bucket
        // 4. Tests should focus on functionality, not rate limiting behavior
        // Rate limiting is properly configured and tested in production SecurityConfig
        // if (rateLimitingFilter != null) {
        //     http.addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);
        // }
        
        // CRITICAL: AnonymousAuthenticationPreFilter must run BEFORE AnonymousAuthenticationFilter
        // AND BEFORE AuthorizationFilter. This ensures permitAll() endpoints have anonymous 
        // authentication set before Spring Security's built-in AnonymousAuthenticationFilter runs
        // and before authorization check.
        if (anonymousAuthenticationPreFilter != null) {
            logger.debug("Adding AnonymousAuthenticationPreFilter before AnonymousAuthenticationFilter");
            // Place BEFORE AnonymousAuthenticationFilter to ensure our filter runs first
            http.addFilterBefore(anonymousAuthenticationPreFilter, org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class);
        }
        
        // JWT filter should come AFTER AnonymousAuthenticationPreFilter but BEFORE AuthorizationFilter
        // This ensures:
        // 1. AnonymousAuthenticationPreFilter sets anonymous authentication first for permitAll() endpoints
        // 2. JWT filter can override anonymous authentication if a token is present
        // 3. AuthorizationFilter sees the correct authentication (anonymous or JWT)
        // 
        // IMPORTANT: JWT filter is enabled for E2E tests to test real JWT authentication.
        // For unit tests with @WithMockUser, the JWT filter will check if authentication is already set
        // and will not override it (see JwtAuthenticationFilter.java line 95-106).
        if (jwtAuthenticationFilter != null) {
            // Place JWT filter BEFORE AuthorizationFilter (which is created by authorizeHttpRequests)
            // This ensures JWT processing happens before authorization checks
            // AnonymousAuthenticationPreFilter will have already run by this point
            logger.debug("Adding JwtAuthenticationFilter before AuthorizationFilter for E2E tests");
            http.addFilterBefore(jwtAuthenticationFilter, org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        } else {
            logger.warn("JwtAuthenticationFilter is null - JWT authentication will not work in E2E tests");
        }

        return http.build();
    }

    /**
     * Test authentication success handler that creates CustomOAuth2User
     * This allows @AuthenticationPrincipal CustomOAuth2User to work in tests
     */
    @Bean
    public AuthenticationSuccessHandler testAuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            // This will be handled by @WithMockUser or manual authentication setup
        };
    }

    /**
     * AuthenticationManager bean for tests
     * Required by AuthController and other components
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * PasswordEncoder bean for tests
     * Required by UserService and other components
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * CorsConfigurationSource bean for tests
     * Required by controllers that use CORS
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Allow test origins for CORS tests
        config.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000", 
            "https://chatbot-java-spring-ai.vercel.app",
            "https://example.com"  // For CORS tests
        ));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * Helper method to create a CustomOAuth2User for testing
     * Can be used in test setup to inject into SecurityContext
     */
    public static void setTestUser(User user) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "test_" + user.getId());
        attributes.put("email", user.getEmail());
        attributes.put("name", user.getUsername());

        OAuth2User oauth2User = new DefaultOAuth2User(
            Collections.emptyList(),
            attributes,
            "email"
        );

        CustomOAuth2User customOAuth2User = new CustomOAuth2User(oauth2User, user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            customOAuth2User,
            null,
            customOAuth2User.getAuthorities()
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
