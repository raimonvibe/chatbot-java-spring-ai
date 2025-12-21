package com.prayer_chat.chatbot.config;

import com.prayer_chat.chatbot.security.CustomOAuth2UserService;
import com.prayer_chat.chatbot.security.JwtAuthenticationFilter;
import com.prayer_chat.chatbot.security.OAuth2AuthenticationSuccessHandler;
import com.prayer_chat.chatbot.security.RateLimitingFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;

/**
 * Production Security Configuration
 * 
 * This configuration is NOT loaded in test profile (@Profile("!test")).
 * In test profile, TestSecurityConfig handles security configuration.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Profile("!test")  // Exclude from test profile - TestSecurityConfig handles tests
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${cors.allowed-origins:http://localhost:3000, https://chatbot-java-spring-ai.vercel.app}")
    private String allowedOrigins;

    @Autowired private RateLimitingFilter rateLimitingFilter;

    @Autowired private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired private CustomOAuth2UserService customOAuth2UserService;

    @Autowired private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @Autowired private org.springframework.core.env.Environment environment;

    @Value("${GOOGLE_CLIENT_ID:${spring.security.oauth2.client.registration.google.client-id:}}")
    private String googleClientId;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/stripe/webhook", "/login/**", "/oauth2/**"))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/chat/**", "/chatbot-widget.js", "/api/health", "/actuator/health").permitAll()
                .requestMatchers("/api/auth/me").authenticated() // Only /me endpoint requires auth
                .requestMatchers("/login/**", "/oauth2/**").permitAll() // OAuth2 endpoints
                .requestMatchers("/stripe/webhook").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                .requestMatchers("/h2-console/**").hasRole("ADMIN")
                // Allow public GET requests to read chatbot data
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/chatbots/**").permitAll()
                // Protect write operations (POST, PUT, DELETE)
                .requestMatchers("/api/chatbots/**").authenticated()
                .requestMatchers("/api/subscription/**").authenticated()
                .requestMatchers("/api/admin/**", "/api/analytics/**").hasRole("ADMIN")
                // Protect Thymeleaf pages (defense-in-depth)
                // In production, nginx reverse proxy will block these entirely
                // In development, they require authentication for access
                .requestMatchers("/", "/index", "/chatbots/**", "/analytics", "/settings").authenticated()
                .anyRequest().authenticated()
            );
        
        // OAuth2 is REQUIRED - configure it
        // Check both the direct env var and the Spring property
        String clientId = googleClientId != null && !googleClientId.trim().isEmpty() 
            ? googleClientId 
            : System.getenv("GOOGLE_CLIENT_ID");
        
        // Also check the Spring property set by EnvironmentVariableConfig
        if ((clientId == null || clientId.trim().isEmpty())) {
            // Use @Value to get the property that was set by EnvironmentVariableConfig
            try {
                clientId = environment.getProperty("spring.security.oauth2.client.registration.google.client-id");
            } catch (Exception e) {
                // Fallback if property not available
                logger.debug("Could not read OAuth2 client-id from environment property");
            }
        }
        
        // Note: This SecurityConfig is NOT loaded in test profile (@Profile("!test"))
        // TestSecurityConfig handles security in test profile
        
        if (clientId == null || clientId.trim().isEmpty()) {
            logger.error("❌ GOOGLE_CLIENT_ID is required but not found!");
            logger.error("❌ Please set GOOGLE_CLIENT_ID in your .env file.");
            throw new IllegalStateException("OAuth2 is required. GOOGLE_CLIENT_ID must be set in .env file or as environment variable.");
        }
        
        if (customOAuth2UserService == null || oAuth2AuthenticationSuccessHandler == null) {
            logger.error("❌ OAuth2 services are not available!");
            logger.error("❌ CustomOAuth2UserService or OAuth2AuthenticationSuccessHandler is null.");
            throw new IllegalStateException("OAuth2 services are required but not configured.");
        }
        
        logger.info("✅ Configuring OAuth2 login with Google (client-id length: {})", clientId.length());
        
        // Configure session management BEFORE OAuth2 to ensure sessions are created for OAuth2 flow
        http
            .sessionManagement(s -> s
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
            );
        
        http.oauth2Login(oauth2 -> oauth2
            .authorizationEndpoint(authorization -> authorization
                .baseUri("/oauth2/authorization")
            )
            .redirectionEndpoint(redirection -> redirection
                .baseUri("/login/oauth2/code/*")
            )
            .userInfoEndpoint(userInfo -> userInfo
                .userService(customOAuth2UserService)
            )
            .successHandler(oAuth2AuthenticationSuccessHandler)
            .failureHandler((request, response, exception) -> {
                // Log the error with more details
                logger.error("OAuth2 authentication failed: {}", exception.getMessage(), exception);
                logger.error("Request URI: {}", request.getRequestURI());
                logger.error("Query String: {}", request.getQueryString());
                logger.error("Session ID: {}", request.getSession(false) != null ? request.getSession(false).getId() : "No session");
                
                // Redirect to frontend with error
                String errorUrl = allowedOrigins.split(",")[0].trim() + "/login?error=oauth2_failed&message=" 
                    + java.net.URLEncoder.encode(exception.getMessage(), java.nio.charset.StandardCharsets.UTF_8);
                try {
                    response.sendRedirect(errorUrl);
                } catch (Exception e) {
                    logger.error("Failed to redirect after OAuth2 failure", e);
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    response.getWriter().write("{\"error\":\"oauth2_failed\",\"message\":\"" + exception.getMessage() + "\"}");
                }
            })
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
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
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
            // Permissions-Policy: Control browser features (added separately due to deprecation)
            .headers(headers -> headers
                .addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter(
                    "Permissions-Policy", "geolocation=(), microphone=(), camera=()"))
            );

        http.addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(Arrays.asList("GET","POST","PUT","DELETE","OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration c) throws Exception {
        return c.getAuthenticationManager();
    }
}
