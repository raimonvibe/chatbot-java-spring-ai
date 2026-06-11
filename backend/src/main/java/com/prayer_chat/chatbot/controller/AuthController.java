package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
import com.prayer_chat.chatbot.util.LogSanitizer;
import com.prayer_chat.chatbot.security.AuthCookieHelper;
import com.prayer_chat.chatbot.security.CustomOAuth2UserService;
import com.prayer_chat.chatbot.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication Controller
 * Handles OAuth2 authentication (Google login only)
 * 
 * Note: Email/password login has been removed. Users must authenticate via Google OAuth2.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private CustomOAuth2UserService customOAuth2UserService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Value("${spring.security.oauth2.client.registration.google.client-id:${GOOGLE_CLIENT_ID:}}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret:${GOOGLE_CLIENT_SECRET:}}")
    private String googleClientSecret;

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    /**
     * When false (default), JWT is only in HttpOnly cookie — not in OAuth JSON (smaller XSS window).
     * Set AUTH_EXPOSE_JWT_IN_RESPONSE=true for clients where cross-site PC_AUTH is dropped (e.g. mobile Safari).
     */
    @Value("${app.auth.expose-jwt-in-oauth-response:false}")
    private boolean exposeJwtInOAuthResponse;

    /**
     * When {@link #exposeJwtInOAuthResponse} is true, JWT lifetime is min(jwt.expiration, this value) so a stolen
     * localStorage bearer expires sooner (milliseconds). Ignored when JSON token is not returned.
     */
    @Value("${app.auth.exposed-bearer-ttl-ms:14400000}")
    private long exposedBearerTtlMs;

    @Autowired
    private RestTemplate restTemplate;

    // Security: Maximum authorization code length (Google codes are typically ~200 chars)
    private static final int MAX_CODE_LENGTH = 500;
    private static final int MIN_CODE_LENGTH = 20;

    /**
     * Get current user info
     * Returns information about the currently authenticated user (via OAuth2)
     *
     * Note: CORS is handled globally by SecurityConfig.corsConfigurationSource()
     * which reads from CORS_ALLOWED_ORIGINS environment variable.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal CustomOAuth2User oAuth2User) {
        if (oAuth2User == null) {
            // Fallback: try to get from SecurityContext (for backward compatibility)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            Object principal = authentication.getPrincipal();
            User user;
            
            if (principal instanceof CustomOAuth2User) {
                user = ((CustomOAuth2User) principal).getUser();
            } else if (principal instanceof User) {
                user = (User) principal;
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("id", user.getId());
            response.put("username", user.getUsername());
            response.put("email", user.getEmail());
            response.put("roles", user.getRoles());
            response.put("authProvider", user.getAuthProvider());
            if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isBlank()) {
                response.put("picture", user.getProfileImageUrl());
            }

            return ResponseEntity.ok(response);
        }

        User user = oAuth2User.getUser();

        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        response.put("roles", user.getRoles());
        response.put("authProvider", user.getAuthProvider());
        if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isBlank()) {
            response.put("picture", user.getProfileImageUrl());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Logout endpoint
     * Invalidates Spring Session and clears SecurityContext
     * Also redirects to Google logout to clear OAuth session
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            @AuthenticationPrincipal CustomOAuth2User oAuth2User) {
        
        try {
            // Log the logout attempt
            if (oAuth2User != null) {
                String email = oAuth2User.getUser().getEmail();
                logger.debug("User logging out: {}", email != null ? LogSanitizer.sanitize(email) : "unknown");
            }

            AuthCookieHelper.clearAuthCookie(response, request.isSecure());

            // Invalidate Spring Session
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            
            // Clear SecurityContext
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                new SecurityContextLogoutHandler().logout(request, response, auth);
            }
            
            // Clear SecurityContextHolder
            SecurityContextHolder.clearContext();
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "Successfully logged out");
            result.put("googleLogoutUrl", "https://accounts.google.com/logout");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            // Even if there's an error, try to clear the session
            try {
                AuthCookieHelper.clearAuthCookie(response, request.isSecure());
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
            } catch (Exception ex) {
                // Ignore cleanup errors
            }
            
            Map<String, Object> error = new HashMap<>();
            error.put("message", "Logout completed (with some errors)");
            return ResponseEntity.ok(error);
        }
    }

    /**
     * Hybrid OAuth2 callback endpoint
     * Exchanges authorization code for tokens and creates user session
     * This endpoint is called by the frontend after Google redirects back with the authorization code
     *
     * Security measures:
     * - Input validation (code length, format)
     * - Redirect URI validation (prevent open redirect attacks)
     * - Error message sanitization (don't leak sensitive info)
     * - Rate limiting (handled by RateLimitingFilter)
     *
     * Note: CORS is handled globally by SecurityConfig.corsConfigurationSource()
     * which reads from CORS_ALLOWED_ORIGINS environment variable.
     */
    // SECURITY: consumes=application/json is load-bearing CSRF protection — HTML forms cannot send
    // JSON content type, and cross-origin fetch with JSON triggers a CORS preflight that is denied
    // for untrusted origins. Do not relax this to accept form-encoded bodies.
    @PostMapping(value = "/oauth2/callback", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> handleOAuth2Callback(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        
        // Security: Validate and sanitize input
        String code = request != null ? request.get("code") : null;
        String redirectUri = request != null ? request.get("redirect_uri") : null;

        // Security: Validate authorization code
        if (code == null || code.trim().isEmpty()) {
            logger.warn("OAuth2 callback: No authorization code provided");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid request"));
        }

        // Security: Validate code length (prevent DoS)
        code = code.trim();
        if (code.length() < MIN_CODE_LENGTH || code.length() > MAX_CODE_LENGTH) {
            logger.warn("OAuth2 callback: Invalid authorization code length: {}", code.length());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid request"));
        }

        // Security: Validate code format (alphanumeric, hyphens, underscores, dots)
        if (!code.matches("^[a-zA-Z0-9._/-]+$")) {
            logger.warn("OAuth2 callback: Invalid authorization code format");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid request"));
        }

        // Security: Validate redirect URI (prevent open redirect attacks)
        if (redirectUri == null || redirectUri.trim().isEmpty()) {
            logger.warn("OAuth2 callback: No redirect URI provided");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid request"));
        }

        redirectUri = redirectUri.trim();
        if (!isValidRedirectUri(redirectUri)) {
            logger.warn("OAuth2 callback: Invalid redirect URI: {}", sanitizeForLogging(redirectUri));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid request"));
        }

        // Security: Validate OAuth credentials are configured
        if (googleClientId == null || googleClientId.isEmpty() || 
            googleClientSecret == null || googleClientSecret.isEmpty()) {
            logger.error("OAuth2 callback: Google OAuth credentials not configured");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Service unavailable"));
        }

        try {
            // Step 1: Exchange authorization code for access token
            logger.info("Exchanging authorization code for access token");
            Map<String, Object> tokenResponse = exchangeCodeForToken(code, redirectUri);
            
            String accessToken = (String) tokenResponse.get("access_token");
            if (accessToken == null) {
                logger.error("OAuth2 callback: No access token in response");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Failed to obtain access token"));
            }

            // Step 2: Get user info from Google using access token
            logger.info("Fetching user info from Google");
            OAuth2User oauth2User = fetchUserInfo(accessToken);

            // Step 3: Process OAuth2 user (create/update in database)
            // We need to create an OAuth2UserRequest to reuse CustomOAuth2UserService
            // For simplicity, we'll extract the logic or create a minimal request
            User user = processOAuth2User(oauth2User);

            // Step 4: Generate JWT (shorter TTL when also returned in JSON — limits XSS / localStorage exposure)
            long configuredTtlMs = jwtTokenProvider.getJwtExpirationMillis();
            long tokenTtlMs = configuredTtlMs;
            if (exposeJwtInOAuthResponse) {
                long capMs = Math.max(60_000L, exposedBearerTtlMs);
                tokenTtlMs = Math.min(configuredTtlMs, capMs);
            }
            String jwtToken = jwtTokenProvider.generateToken(user.getEmail(), tokenTtlMs);
            logger.info("Generated JWT token for user: {}", LogSanitizer.sanitize(user.getEmail()));

            long cookieMaxAgeSeconds = Math.max(1L, tokenTtlMs / 1000L);
            AuthCookieHelper.addAuthCookie(
                httpResponse,
                jwtToken,
                cookieMaxAgeSeconds,
                httpRequest.isSecure()
            );

            // Step 5: Create session (optional, for session-based auth)
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute("user", user);

            // Step 6: Return user info (JWT in HttpOnly cookie; optional JSON token for local HTTP / tests)
            Map<String, Object> response = new HashMap<>();
            if (exposeJwtInOAuthResponse) {
                response.put("token", jwtToken);
            }
            // User doesn't have a name field, use email as display name
            response.put("user", Map.of(
                    "id", user.getId(),
                    "email", user.getEmail(),
                    "username", user.getUsername(),
                    "name", user.getEmail(), // Use email as display name
                    "authProvider", user.getAuthProvider() != null ? user.getAuthProvider().name() : "GOOGLE"
            ));

            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setCacheControl(CacheControl.noStore());
            responseHeaders.setPragma("no-cache");
            return new ResponseEntity<>(response, responseHeaders, HttpStatus.OK);

        } catch (RuntimeException e) {
            // Check if it's an invalid_grant error (expired/used code)
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("expired or already used")) {
                logger.warn("OAuth2 callback: Authorization code expired or already used - user should retry login");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                            "error", "Authorization code expired",
                            "message", "The authorization code has expired or was already used. Please try logging in again."
                        ));
            }
            // Security: Don't leak sensitive error details for other errors
            logger.error("OAuth2 callback failed: {}", LogSanitizer.sanitizeForLogging(errorMessage));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication failed"));
        } catch (Exception e) {
            // Security: Log full error but don't expose to client
            logger.error("OAuth2 callback failed with unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Authentication failed"));
        }
    }

    /**
     * Security: Validate redirect URI to prevent open redirect attacks.
     * Only allow redirect URIs whose host exactly matches an allowed origin (no subdomain bypass).
     */
    private boolean isValidRedirectUri(String redirectUri) {
        if (redirectUri == null || redirectUri.trim().isEmpty()) {
            return false;
        }
        String trimmed = redirectUri.trim();
        if (trimmed.length() > 2048) return false;

        if (!trimmed.startsWith("http://localhost") && !trimmed.startsWith("http://127.0.0.1") && !trimmed.startsWith("https://")) {
            return false;
        }
        if (!trimmed.contains("/auth/callback")) return false;

        java.net.URI redirectUriParsed;
        try {
            redirectUriParsed = java.net.URI.create(trimmed);
        } catch (Exception e) {
            return false;
        }
        String redirectHost = redirectUriParsed.getHost();
        if (redirectHost == null || redirectHost.isBlank()) return false;
        if (redirectUriParsed.getUserInfo() != null && !redirectUriParsed.getUserInfo().isEmpty()) return false;

        if (allowedOrigins == null || allowedOrigins.trim().isEmpty()) {
            return "localhost".equalsIgnoreCase(redirectHost) || "127.0.0.1".equals(redirectHost);
        }

        for (String origin : allowedOrigins.split(",")) {
            String base = origin.trim();
            if (base.isEmpty()) continue;
            try {
                java.net.URI originUri = java.net.URI.create(base);
                String originHost = originUri.getHost();
                if (originHost != null && originHost.equalsIgnoreCase(redirectHost)) return true;
            } catch (Exception ignored) {
                continue;
            }
        }
        return false;
    }

    /**
     * Security: Sanitize sensitive data for logging
     */
    private String sanitizeForLogging(String input) {
        if (input == null) return "null";
        if (input.length() > 50) {
            return input.substring(0, 50) + "...";
        }
        return input;
    }

    /**
     * Exchange authorization code for access token
     */
    private Map<String, Object> exchangeCodeForToken(String code, String redirectUri) {
        String tokenUrl = "https://oauth2.googleapis.com/token";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", googleClientId);
        params.add("client_secret", googleClientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.postForEntity(tokenUrl, request, Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                // Check for specific OAuth errors
                Map<String, Object> errorBody = response.getBody();
                if (errorBody != null && errorBody.containsKey("error")) {
                    String error = (String) errorBody.get("error");
                    String errorDescription = errorBody.containsKey("error_description") 
                        ? (String) errorBody.get("error_description") 
                        : "Unknown error";
                    
                    logger.error("OAuth2 token exchange failed: {} - {}",
                        LogSanitizer.sanitizeForLogging(error),
                        LogSanitizer.sanitizeForLogging(errorDescription));
                    
                    // Provide more specific error messages
                    if ("invalid_grant".equals(error)) {
                        throw new RuntimeException("Authorization code expired or already used. Please try logging in again.");
                    } else if ("invalid_client".equals(error)) {
                        throw new RuntimeException("OAuth client configuration error");
                    } else if ("redirect_uri_mismatch".equals(error)) {
                        throw new RuntimeException("Redirect URI mismatch. Please contact support.");
                    } else {
                        throw new RuntimeException("OAuth authentication failed: " + error);
                    }
                }
                throw new RuntimeException("Failed to exchange code for token: " + response.getStatusCode());
            }

            if (response.getBody() == null) {
                throw new RuntimeException("Empty response from OAuth token endpoint");
            }

            return response.getBody();
        } catch (RestClientException e) {
            // Check if the error response contains invalid_grant
            // RestClientException might wrap an HTTP error response
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("invalid_grant")) {
                logger.error("OAuth2 callback failed: {} on POST request for \"{}\": \"{}\"",
                    e.getClass().getSimpleName(),
                    tokenUrl,
                    LogSanitizer.sanitizeForLogging(errorMessage));
                throw new RuntimeException("Authorization code expired or already used. Please try logging in again.");
            }
            // Log the full error for debugging
            logger.error("OAuth2 callback failed: {} on POST request for \"{}\": \"{}\"",
                e.getClass().getSimpleName(),
                tokenUrl,
                LogSanitizer.sanitizeForLogging(errorMessage));
            throw new RuntimeException("Failed to exchange authorization code: " + errorMessage, e);
        }
    }

    /**
     * Fetch user info from Google using access token
     */
    private OAuth2User fetchUserInfo(String accessToken) {
        String userInfoUrl = "https://www.googleapis.com/oauth2/v3/userinfo";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<?> request = new HttpEntity<>(headers);

        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.exchange(
                userInfoUrl,
                HttpMethod.GET,
                request,
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Failed to fetch user info: " + response.getStatusCode());
        }

        Map<String, Object> attributes = response.getBody();
        
        // Create OAuth2User from attributes
        return new DefaultOAuth2User(
                java.util.Collections.singletonList(() -> "USER"),
                attributes,
                "sub" // name attribute key
        );
    }

    /**
     * Process OAuth2 user - create or update user in database
     * This reuses the logic from CustomOAuth2UserService
     */
    private User processOAuth2User(OAuth2User oauth2User) {
        // Extract user info from OAuth2User
        String googleId = oauth2User.getAttribute("sub");
        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");

        if (email == null || email.isEmpty()) {
            throw new RuntimeException("Email not found from OAuth2 provider");
        }

            // Reuse CustomOAuth2UserService logic
            return customOAuth2UserService.processOAuth2UserDirectly(oauth2User, googleId, email, name);
    }
}
