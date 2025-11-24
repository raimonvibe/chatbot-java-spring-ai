package com.tjanabot.chatbot.controller;

import com.stripe.exception.StripeException;
import com.tjanabot.chatbot.model.Subscription;
import com.tjanabot.chatbot.model.User;
import com.tjanabot.chatbot.repository.SubscriptionRepository;
import com.tjanabot.chatbot.security.CustomOAuth2User;
import com.tjanabot.chatbot.service.StripeService;
import com.tjanabot.chatbot.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for managing user subscriptions
 */
@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionController.class);

    @Autowired
    private StripeService stripeService;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    /**
     * Get current user's subscription status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSubscriptionStatus(
            @AuthenticationPrincipal CustomOAuth2User currentUser) {

        try {
            User user = currentUser.getUser();
            Optional<Subscription> subscriptionOpt = subscriptionRepository.findByUserId(user.getId());

            Map<String, Object> response = new HashMap<>();

            if (subscriptionOpt.isPresent()) {
                Subscription subscription = subscriptionOpt.get();
                response.put("hasSubscription", true);
                response.put("status", subscription.getStatus());
                response.put("plan", subscription.getPlan());
                response.put("isActive", subscription.isActive());
                response.put("canUseChatbot", subscription.canUseChatbot());
                response.put("currentPeriodEnd", subscription.getCurrentPeriodEnd());

                if (subscription.getCanceledAt() != null) {
                    response.put("canceledAt", subscription.getCanceledAt());
                }
            } else {
                response.put("hasSubscription", false);
                response.put("isActive", false);
                response.put("canUseChatbot", false);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error retrieving subscription status: {}", LogSanitizer.sanitizeException(e));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Create a Stripe checkout session for subscription
     */
    @PostMapping("/create-checkout-session")
    public ResponseEntity<Map<String, String>> createCheckoutSession(
            @AuthenticationPrincipal CustomOAuth2User currentUser) {

        try {
            User user = currentUser.getUser();

            // Check if user already has an active subscription
            Optional<Subscription> existingSubscription = subscriptionRepository.findByUserId(user.getId());
            if (existingSubscription.isPresent() && existingSubscription.get().canUseChatbot()) {
                Map<String, String> response = new HashMap<>();
                response.put("error", "User already has an active subscription");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            String checkoutUrl = stripeService.createCheckoutSession(user);

            Map<String, String> response = new HashMap<>();
            response.put("checkoutUrl", checkoutUrl);

            logger.info("Created checkout session for user: {}", LogSanitizer.sanitize(user.getEmail()));
            return ResponseEntity.ok(response);

        } catch (StripeException e) {
            logger.error("Stripe error creating checkout session: {}", LogSanitizer.sanitizeException(e));
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to create checkout session");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        } catch (Exception e) {
            logger.error("Error creating checkout session: {}", LogSanitizer.sanitizeException(e));
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Cancel current user's subscription
     */
    @PostMapping("/cancel")
    public ResponseEntity<Map<String, String>> cancelSubscription(
            @AuthenticationPrincipal CustomOAuth2User currentUser) {

        try {
            User user = currentUser.getUser();
            stripeService.cancelSubscription(user.getId());

            Map<String, String> response = new HashMap<>();
            response.put("message", "Subscription canceled successfully");

            logger.info("Canceled subscription for user: {}", LogSanitizer.sanitize(user.getEmail()));
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("Error canceling subscription: {}", LogSanitizer.sanitize(e.getMessage()));
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (StripeException e) {
            logger.error("Stripe error canceling subscription: {}", LogSanitizer.sanitizeException(e));
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to cancel subscription");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        } catch (Exception e) {
            logger.error("Error canceling subscription: {}", LogSanitizer.sanitizeException(e));
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Get subscription details
     */
    @GetMapping("/details")
    public ResponseEntity<Subscription> getSubscriptionDetails(
            @AuthenticationPrincipal CustomOAuth2User currentUser) {

        try {
            User user = currentUser.getUser();
            Optional<Subscription> subscriptionOpt = subscriptionRepository.findByUserId(user.getId());

            return subscriptionOpt
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());

        } catch (Exception e) {
            logger.error("Error retrieving subscription details: {}", LogSanitizer.sanitizeException(e));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
