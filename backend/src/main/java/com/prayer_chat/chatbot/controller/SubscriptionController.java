package com.prayer_chat.chatbot.controller;

import com.stripe.exception.StripeException;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
import com.prayer_chat.chatbot.service.StripeService;
import com.prayer_chat.chatbot.util.LogSanitizer;
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

    /**
     * Change subscription plan (upgrade or downgrade)
     */
    @PostMapping("/change-plan")
    public ResponseEntity<Map<String, String>> changePlan(
            @AuthenticationPrincipal CustomOAuth2User currentUser,
            @RequestBody Map<String, String> request) {

        try {
            User user = currentUser.getUser();
            String newPriceId = request.get("priceId");
            String planStr = request.get("plan");

            if (newPriceId == null || planStr == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Missing required fields: priceId and plan");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            Subscription.SubscriptionPlan newPlan;
            try {
                newPlan = Subscription.SubscriptionPlan.valueOf(planStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid plan: " + planStr);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            stripeService.changeSubscriptionPlan(user.getId(), newPriceId, newPlan);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Subscription plan changed successfully to " + newPlan);

            logger.info("Changed subscription plan for user {} to: {}",
                LogSanitizer.sanitize(user.getEmail()), newPlan);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("Error changing plan: {}", LogSanitizer.sanitize(e.getMessage()));
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (StripeException e) {
            logger.error("Stripe error changing plan: {}", LogSanitizer.sanitizeException(e));
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to change subscription plan");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        } catch (Exception e) {
            logger.error("Error changing plan: {}", LogSanitizer.sanitizeException(e));
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Upgrade subscription plan
     */
    @PostMapping("/upgrade")
    public ResponseEntity<Map<String, String>> upgradePlan(
            @AuthenticationPrincipal CustomOAuth2User currentUser,
            @RequestBody Map<String, String> request) {

        try {
            User user = currentUser.getUser();
            String newPriceId = request.get("priceId");
            String planStr = request.get("plan");

            if (newPriceId == null || planStr == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Missing required fields: priceId and plan");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            Subscription.SubscriptionPlan newPlan;
            try {
                newPlan = Subscription.SubscriptionPlan.valueOf(planStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid plan: " + planStr);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            stripeService.upgradeSubscription(user.getId(), newPriceId, newPlan);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Subscription upgraded successfully to " + newPlan);

            logger.info("Upgraded subscription for user {} to: {}",
                LogSanitizer.sanitize(user.getEmail()), newPlan);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("Error upgrading: {}", LogSanitizer.sanitize(e.getMessage()));
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (StripeException e) {
            logger.error("Stripe error upgrading: {}", LogSanitizer.sanitizeException(e));
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to upgrade subscription");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        } catch (Exception e) {
            logger.error("Error upgrading: {}", LogSanitizer.sanitizeException(e));
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Downgrade subscription plan
     */
    @PostMapping("/downgrade")
    public ResponseEntity<Map<String, String>> downgradePlan(
            @AuthenticationPrincipal CustomOAuth2User currentUser,
            @RequestBody Map<String, String> request) {

        try {
            User user = currentUser.getUser();
            String newPriceId = request.get("priceId");
            String planStr = request.get("plan");

            if (newPriceId == null || planStr == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Missing required fields: priceId and plan");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            Subscription.SubscriptionPlan newPlan;
            try {
                newPlan = Subscription.SubscriptionPlan.valueOf(planStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid plan: " + planStr);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            stripeService.downgradeSubscription(user.getId(), newPriceId, newPlan);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Subscription will be downgraded to " + newPlan + " at the end of the billing period");

            logger.info("Scheduled downgrade for user {} to: {}",
                LogSanitizer.sanitize(user.getEmail()), newPlan);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("Error downgrading: {}", LogSanitizer.sanitize(e.getMessage()));
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (StripeException e) {
            logger.error("Stripe error downgrading: {}", LogSanitizer.sanitizeException(e));
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to downgrade subscription");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        } catch (Exception e) {
            logger.error("Error downgrading: {}", LogSanitizer.sanitizeException(e));
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
