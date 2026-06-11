package com.prayer_chat.chatbot.security;

import com.prayer_chat.chatbot.config.BillingProperties;
import com.prayer_chat.chatbot.config.FrontendBaseUrlProvider;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Handles successful server-side OAuth2 login ({@code /login/oauth2/code/*}).
 * Post-login redirects use {@link FrontendBaseUrlProvider} so the target base URL is never derived from request headers.
 */
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private BillingProperties billingProperties;

    @Autowired
    private FrontendBaseUrlProvider frontendBaseUrlProvider;

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
                try {
                    subscriptionRepository.save(freeSubscription);
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    // Concurrent first login created the subscription already (unique user_id) — safe to continue.
                    logger.info("FREE subscription already created concurrently for user {}", user.getId());
                }

                logger.info("User {} created with FREE plan, redirecting to onboarding", LogSanitizer.sanitize(user.getEmail()));
                String redirectUrl = frontendBaseUrlProvider.getBaseUrl() + "/onboarding?welcome=true";
                getRedirectStrategy().sendRedirect(request, response, redirectUrl);
                return;
            }

            Subscription subscription = subscriptionOpt.get();

            if (!subscription.canUseChatbot()) {
                if (!billingProperties.isEnabled()) {
                    logger.info("User {} has inactive subscription; billing off — redirecting to dashboard",
                        LogSanitizer.sanitize(user.getEmail()));
                    getRedirectStrategy().sendRedirect(request, response, frontendBaseUrlProvider.getBaseUrl() + "/dashboard");
                    return;
                }
                // Has subscription but inactive - redirect to pricing
                logger.info("User {} has inactive subscription, redirecting to pricing", LogSanitizer.sanitize(user.getEmail()));
                String redirectUrl = frontendBaseUrlProvider.getBaseUrl() + "/pricing?upgrade=true";
                getRedirectStrategy().sendRedirect(request, response, redirectUrl);
                return;
            }

            // User has active subscription - redirect to dashboard
            // Dashboard will check for chatbots and redirect to onboarding if needed
            logger.info("User {} logged in successfully with active subscription", LogSanitizer.sanitize(user.getEmail()));
            String redirectUrl = frontendBaseUrlProvider.getBaseUrl() + "/dashboard";
            getRedirectStrategy().sendRedirect(request, response, redirectUrl);
        } else {
            // Fallback
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }
}
