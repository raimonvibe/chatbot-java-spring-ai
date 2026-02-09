package com.prayer_chat.chatbot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Custom filter to ensure anonymous authentication is set for permitAll() endpoints
 * BEFORE AuthorizationFilter runs.
 * 
 * This filter runs early in the filter chain to ensure that permitAll() endpoints
 * have anonymous authentication set before Spring Security's authorization check.
 * 
 * This is specifically needed for E2E tests where REST Assured doesn't properly
 * trigger Spring Security's AnonymousAuthenticationFilter in the same way MockMvc does.
 */
@Component
@Profile("test")  // Only load in test profile
public class AnonymousAuthenticationPreFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AnonymousAuthenticationPreFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String requestUri = request.getRequestURI();
        logger.debug("🔍 AnonymousAuthenticationPreFilter: Processing request URI: {}", requestUri);
        
        // Check if this is a permitAll() endpoint (must match TestSecurityConfig and production SecurityConfig)
        boolean isPermitAllEndpoint = requestUri.startsWith("/api/chat/") || 
                                      requestUri.equals("/api/health") ||
                                      requestUri.startsWith("/api/auth/") ||
                                      requestUri.startsWith("/login/") ||
                                      requestUri.startsWith("/oauth2/") ||
                                      requestUri.startsWith("/stripe/webhook") ||
                                      requestUri.startsWith("/css/") ||
                                      requestUri.startsWith("/js/") ||
                                      requestUri.startsWith("/images/") ||
                                      requestUri.startsWith("/webjars/") ||
                                      (requestUri.startsWith("/api/chatbots") && "GET".equals(request.getMethod())) ||
                                      (requestUri.equals("/api/plans/limits") && "GET".equals(request.getMethod()));
        
        // Check current authentication state
        var currentAuth = SecurityContextHolder.getContext().getAuthentication();
        logger.debug("🔍 AnonymousAuthenticationPreFilter: isPermitAllEndpoint={}, currentAuth={}", 
            isPermitAllEndpoint,
            currentAuth != null ? currentAuth.getClass().getSimpleName() : "null");
        
        // For permitAll() endpoints, ensure anonymous authentication is set if no authentication exists
        if (isPermitAllEndpoint && currentAuth == null) {
            logger.debug("🔍 AnonymousAuthenticationPreFilter: Setting anonymous authentication for permitAll() endpoint");
            org.springframework.security.authentication.AnonymousAuthenticationToken anonymousAuth =
                new org.springframework.security.authentication.AnonymousAuthenticationToken(
                    "anonymous-key",
                    "anonymousUser",
                    org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
                );
            SecurityContextHolder.getContext().setAuthentication(anonymousAuth);
            logger.debug("🔍 AnonymousAuthenticationPreFilter: Anonymous authentication set successfully");
        }
        
        filterChain.doFilter(request, response);
    }
}

