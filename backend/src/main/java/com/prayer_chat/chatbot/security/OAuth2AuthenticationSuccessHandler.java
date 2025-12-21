package com.prayer_chat.chatbot.security;

import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
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

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;
    
    @Value("${FRONTEND_URL:}")
    private String frontendUrlOverride;
    
    /**
     * Get the frontend URL for redirects
     * Priority: 1. FRONTEND_URL env var, 2. First non-localhost URL from CORS, 3. First URL, 4. Default
     */
    private String getFrontendUrl() {
        // Use explicit FRONTEND_URL if set
        if (frontendUrlOverride != null && !frontendUrlOverride.trim().isEmpty()) {
            logger.debug("Using FRONTEND_URL override: {}", frontendUrlOverride);
            return frontendUrlOverride.trim();
        }
        
        if (allowedOrigins == null || allowedOrigins.trim().isEmpty()) {
            return "http://localhost:3000";
        }
        
        // Parse comma-separated list
        String[] origins = allowedOrigins.split(",");
        
        // First, try to find a non-localhost URL (for production)
        for (String origin : origins) {
            String trimmed = origin.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("http://localhost") && !trimmed.startsWith("http://127.0.0.1")) {
                // Remove wildcard patterns if present
                trimmed = trimmed.replace("https://*.", "https://");
                logger.debug("Using non-localhost origin: {}", trimmed);
                return trimmed;
            }
        }
        
        // Fallback to first URL (for local development)
        String firstOrigin = origins[0].trim();
        firstOrigin = firstOrigin.replace("https://*.", "https://");
        logger.debug("Using first origin (may be localhost): {}", firstOrigin);
        return firstOrigin;
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
                logger.info("Creating FREE subscription for new user: {}", user.getEmail());
                Subscription freeSubscription = new Subscription();
                freeSubscription.setUser(user);
                freeSubscription.setStripeCustomerId("free_" + user.getId());
                freeSubscription.setPlan(Subscription.SubscriptionPlan.FREE);
                freeSubscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
                subscriptionRepository.save(freeSubscription);

                logger.info("User {} created with FREE plan, redirecting to dashboard", user.getEmail());
                String redirectUrl = getFrontendUrl() + "/dashboard?welcome=true";
                getRedirectStrategy().sendRedirect(request, response, redirectUrl);
                return;
            }

            Subscription subscription = subscriptionOpt.get();

            if (!subscription.canUseChatbot()) {
                // Has subscription but inactive - redirect to pricing
                logger.info("User {} has inactive subscription, redirecting to pricing", user.getEmail());
                String redirectUrl = getFrontendUrl() + "/pricing?upgrade=true";
                getRedirectStrategy().sendRedirect(request, response, redirectUrl);
                return;
            }

            // User has active subscription - redirect to dashboard
            logger.info("User {} logged in successfully with active subscription", user.getEmail());
            String redirectUrl = getFrontendUrl() + "/dashboard";
            getRedirectStrategy().sendRedirect(request, response, redirectUrl);
        } else {
            // Fallback
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }
}
