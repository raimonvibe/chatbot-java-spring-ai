package com.prayer_chat.chatbot.security;

import com.prayer_chat.chatbot.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component   // ← DIT IS ALLES WAT JE NODIG HEBT
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    // Constructor injection → Spring maakt deze bean automatisch aan
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        logger.debug("🔍 JwtAuthenticationFilter: Processing request URI: {}", requestUri);
        
        // For permitAll() endpoints, ensure anonymous authentication is set
        // This is a defensive measure to ensure permitAll() endpoints work correctly
        // even if AnonymousAuthenticationFilter hasn't run yet or if there's a filter order issue
        boolean isPermitAllEndpoint = requestUri.startsWith("/api/chat/") || 
                                      requestUri.equals("/api/health") ||
                                      requestUri.startsWith("/api/auth/") ||
                                      requestUri.startsWith("/login/") ||
                                      requestUri.startsWith("/oauth2/");
        
        logger.debug("🔍 JwtAuthenticationFilter: isPermitAllEndpoint={}, currentAuth={}", 
            isPermitAllEndpoint, 
            SecurityContextHolder.getContext().getAuthentication() != null ? 
                SecurityContextHolder.getContext().getAuthentication().getClass().getSimpleName() : "null");
        
        // Check if there's a JWT token in the request
        String jwt = getJwtFromRequest(request);
        
        // If no JWT token and this is a permitAll() endpoint, ensure anonymous authentication is set
        if (!StringUtils.hasText(jwt) && isPermitAllEndpoint) {
            logger.debug("🔍 JwtAuthenticationFilter: No JWT token, permitAll() endpoint - ensuring anonymous auth");
            // Ensure anonymous authentication is set for permitAll() endpoints
            if (SecurityContextHolder.getContext().getAuthentication() == null ||
                SecurityContextHolder.getContext().getAuthentication() instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
                // Set or keep anonymous authentication
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    org.springframework.security.authentication.AnonymousAuthenticationToken anonymousAuth =
                        new org.springframework.security.authentication.AnonymousAuthenticationToken(
                            "anonymous-key",
                            "anonymousUser",
                            org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
                        );
                    SecurityContextHolder.getContext().setAuthentication(anonymousAuth);
                    logger.debug("🔍 JwtAuthenticationFilter: Set anonymous authentication for permitAll() endpoint");
                } else {
                    logger.debug("🔍 JwtAuthenticationFilter: Anonymous authentication already set");
                }
            }
            // Continue with anonymous authentication
            logger.debug("🔍 JwtAuthenticationFilter: Continuing filter chain with anonymous auth");
            filterChain.doFilter(request, response);
            return;
        }
        
        // If no JWT token and this is NOT a permitAll() endpoint, let existing authentication handle it
        if (!StringUtils.hasText(jwt)) {
            logger.debug("🔍 JwtAuthenticationFilter: No JWT token, not permitAll() - continuing with existing auth");
            filterChain.doFilter(request, response);
            return;
        }

        // There's a JWT token - process it (will override anonymous authentication if present)
        logger.debug("🔍 JwtAuthenticationFilter: JWT token found, processing authentication");
        try {

            if (StringUtils.hasText(jwt)) {
                logger.debug("JWT token found in request: {}", jwt.substring(0, Math.min(20, jwt.length())) + "...");
                boolean isValid = jwtTokenProvider.validateToken(jwt);
                logger.debug("JWT token validation result: {}", isValid);
                
                if (isValid) {
                    String username = jwtTokenProvider.getUsernameFromToken(jwt);
                    logger.debug("Extracted username from token: {}", username);

                    // Check if username is valid before loading user details
                    if (StringUtils.hasText(username)) {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                        
                        // Convert UserDetails (User entity) to CustomOAuth2User for controller compatibility
                        // Controllers expect @AuthenticationPrincipal CustomOAuth2User
                        if (userDetails instanceof User) {
                            User user = (User) userDetails;
                            
                            // Create OAuth2User attributes from User entity
                            Map<String, Object> attributes = new HashMap<>();
                            attributes.put("sub", user.getGoogleId() != null ? user.getGoogleId() : "jwt_" + user.getId());
                            attributes.put("email", user.getEmail());
                            attributes.put("name", user.getUsername());
                            
                            OAuth2User oauth2User = new DefaultOAuth2User(
                                Collections.emptyList(),
                                attributes,
                                "email"
                            );
                            
                            CustomOAuth2User customOAuth2User = new CustomOAuth2User(oauth2User, user);
                            
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(customOAuth2User, null, customOAuth2User.getAuthorities());
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            logger.debug("Authenticated user: {} as CustomOAuth2User", username);
                        } else {
                            // Fallback for non-User UserDetails (shouldn't happen in our app)
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            logger.debug("Authenticated user: {} as UserDetails", username);
                        }
                    } else {
                        logger.warn("JWT token contained null or empty username");
                    }
                } else {
                    logger.warn("JWT token validation failed for token: {}", jwt.substring(0, Math.min(20, jwt.length())) + "...");
                }
            } else {
                logger.debug("No JWT token found in request");
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}