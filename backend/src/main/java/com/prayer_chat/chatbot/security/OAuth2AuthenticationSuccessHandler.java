package com.prayer_chat.chatbot.security;

import com.prayer_chat.chatbot.config.BillingProperties;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import com.prayer_chat.chatbot.util.LogSanitizer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Handles successful OAuth2 authentication
 */
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private BillingProperties billingProperties;

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;
    
    @Value("${FRONTEND_URL:}")
    private String frontendUrlOverride;
    
    /**
     * Get the frontend URL for redirects
     * Priority: 1. FRONTEND_URL env var, 2. Request origin (if localhost, use localhost), 3. Production URL from CORS
     */
    private String getFrontendUrl(HttpServletRequest request) {
        // Use explicit FRONTEND_URL if set
        if (frontendUrlOverride != null && !frontendUrlOverride.trim().isEmpty()) {
            logger.info("Using FRONTEND_URL override: {}", frontendUrlOverride);
            return frontendUrlOverride.trim();
        }
        
        // Check if request is coming from localhost - if so, use localhost for redirect
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        String host = request.getHeader("Host");
        
        boolean isLocalRequest = (origin != null && (origin.startsWith("http://localhost") || origin.startsWith("http://127.0.0.1"))) ||
                                 (referer != null && (referer.startsWith("http://localhost") || referer.startsWith("http://127.0.0.1"))) ||
                                 (host != null && (host.startsWith("localhost") || host.startsWith("127.0.0.1")));
        
        if (isLocalRequest) {
            logger.info("Detected localhost request (origin: {}, referer: {}, host: {}), using localhost redirect", origin, referer, host);
            return "http://localhost:3000";
        }
        
        if (allowedOrigins == null || allowedOrigins.trim().isEmpty()) {
            logger.warn("No allowed origins configured, using default localhost");
            return "http://localhost:3000";
        }
        
        // Parse comma-separated list
        String[] origins = allowedOrigins.split(",");
        
        // First, try to find a non-localhost URL (for production)
        // Priority: 1. prayer-chat.com (production), 2. vercel.app (testing), 3. Other HTTPS, 4. First non-localhost
        String productionUrl = null;
        String testUrl = null;
        String firstNonLocalhost = null;
        String localhostUrl = null;
        
        for (String originConfig : origins) {
            String trimmed = originConfig.trim();
            if (trimmed.isEmpty()) continue;
            
            // Remove wildcard patterns if present
            trimmed = trimmed.replace("https://*.", "https://");
            
            // Track localhost URLs separately
            if (trimmed.startsWith("http://localhost") || trimmed.startsWith("http://127.0.0.1")) {
                if (localhostUrl == null) {
                    localhostUrl = trimmed;
                }
                continue;
            }
            
            // Track first non-localhost URL
            if (firstNonLocalhost == null) {
                firstNonLocalhost = trimmed;
            }
            
            // Prioritize prayer-chat.com (production domain) - check both www and non-www
            if (trimmed.contains("prayer-chat.com")) {
                // Prefer www.prayer-chat.com if available, otherwise use prayer-chat.com
                if (trimmed.contains("www.prayer-chat.com")) {
                    productionUrl = trimmed;
                    break; // Highest priority, use immediately
                } else if (productionUrl == null) {
                    productionUrl = trimmed; // Store non-www version as fallback
                }
            }
            
            // Track vercel.app URLs for testing (but don't break, continue to check for prayer-chat.com)
            if (testUrl == null && trimmed.contains("vercel.app")) {
                testUrl = trimmed;
            }
        }
        
        // Use production URL if found, otherwise test URL, otherwise first non-localhost, otherwise localhost, otherwise first URL
        String selectedUrl = productionUrl != null ? productionUrl :
                            (testUrl != null ? testUrl :
                            (firstNonLocalhost != null ? firstNonLocalhost :
                            (localhostUrl != null ? localhostUrl : origins[0].trim())));
        
        logger.info("Selected frontend URL for redirect: {} (from allowed origins: {})", selectedUrl, allowedOrigins);
        return selectedUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                       Authentication authentication) throws IOException, ServletException {

        if (authentication.getPrincipal() instanceof CustomOAuth2User) {
            CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
            User user = oAuth2User.getUser();

            // Check if user has a subscription
            Optional<Subscription> subscriptionOpt = subscriptionRepository.findByUserId(user.getId());

            if (subscriptionOpt.isEmpty()) {
                // No subscription - create FREE plan automatically
                logger.info("Creating FREE subscription for new user: {}", LogSanitizer.sanitize(user.getEmail()));
                Subscription freeSubscription = new Subscription();
                freeSubscription.setUser(user);
                freeSubscription.setStripeCustomerId("free_" + user.getId());
                freeSubscription.setPlan(Subscription.SubscriptionPlan.FREE);
                freeSubscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
                subscriptionRepository.save(freeSubscription);

                logger.info("User {} created with FREE plan, redirecting to onboarding", LogSanitizer.sanitize(user.getEmail()));
                String redirectUrl = getFrontendUrl(request) + "/onboarding?welcome=true";
                getRedirectStrategy().sendRedirect(request, response, redirectUrl);
                return;
            }

            Subscription subscription = subscriptionOpt.get();

            if (!subscription.canUseChatbot()) {
                if (!billingProperties.isEnabled()) {
                    logger.info("User {} has inactive subscription; billing off — redirecting to dashboard",
                        LogSanitizer.sanitize(user.getEmail()));
                    getRedirectStrategy().sendRedirect(request, response, getFrontendUrl(request) + "/dashboard");
                    return;
                }
                // Has subscription but inactive - redirect to pricing
                logger.info("User {} has inactive subscription, redirecting to pricing", LogSanitizer.sanitize(user.getEmail()));
                String redirectUrl = getFrontendUrl(request) + "/pricing?upgrade=true";
                getRedirectStrategy().sendRedirect(request, response, redirectUrl);
                return;
            }

            // User has active subscription - redirect to dashboard
            // Dashboard will check for chatbots and redirect to onboarding if needed
            logger.info("User {} logged in successfully with active subscription", LogSanitizer.sanitize(user.getEmail()));
            String redirectUrl = getFrontendUrl(request) + "/dashboard";
            getRedirectStrategy().sendRedirect(request, response, redirectUrl);
        } else {
            // Fallback
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }
}
