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
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

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
                response.put("status", "FREE");
                response.put("plan", "FREE");
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
     * Sync subscription from a Stripe checkout session (e.g. after payment redirect when webhook hasn't run yet).
     * Validates that the session belongs to the current user via client_reference_id.
     */
    @PostMapping("/sync-from-session")
    public ResponseEntity<Map<String, Object>> syncFromCheckoutSession(
            @AuthenticationPrincipal CustomOAuth2User currentUser,
            @RequestBody(required = false) Map<String, String> body) {

        if (currentUser == null || currentUser.getUser() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String sessionId = body != null ? body.get("session_id") : null;
        if (sessionId == null || sessionId.isBlank()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "session_id is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }
        String trimmed = sessionId.trim();
        if (trimmed.length() > 255) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Invalid session ID format");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }
        try {
            boolean synced = stripeService.syncSubscriptionFromCheckoutSession(trimmed, currentUser.getUser().getId());
            Optional<Subscription> subOpt = subscriptionRepository.findByUserId(currentUser.getUser().getId());
            Map<String, Object> response = new HashMap<>();
            if (subOpt.isPresent()) {
                Subscription s = subOpt.get();
                response.put("hasSubscription", true);
                response.put("status", s.getStatus());
                response.put("plan", s.getPlan());
                response.put("isActive", s.isActive());
                response.put("canUseChatbot", s.canUseChatbot());
                response.put("currentPeriodEnd", s.getCurrentPeriodEnd());
                if (s.getCanceledAt() != null) response.put("canceledAt", s.getCanceledAt());
            } else {
                response.put("hasSubscription", false);
                response.put("status", "FREE");
                response.put("plan", "FREE");
                response.put("isActive", false);
                response.put("canUseChatbot", false);
            }
            response.put("synced", synced);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        } catch (StripeException e) {
            logger.warn("Sync from session failed: {}", LogSanitizer.sanitizeException(e));
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Invalid or expired session");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }
    }

    /**
     * Create a Stripe checkout session for subscription
     */
    @PostMapping("/create-checkout-session")
    public ResponseEntity<Map<String, String>> createCheckoutSession(
            @AuthenticationPrincipal CustomOAuth2User currentUser,
            @RequestBody(required = false) Map<String, String> request) {

        try {
            User user = currentUser.getUser();

            // Resolve plan or priceId: prefer "plan" (BASIC, PRO, ENTERPRISE), else "priceId" (Stripe price_xxx)
            String planOrPriceId = null;
            if (request != null) {
                if (request.containsKey("plan")) {
                    String plan = request.get("plan");
                    if (plan != null && !plan.trim().isEmpty()) {
                        String p = plan.trim().toUpperCase();
                        if ("BASIC".equals(p) || "PRO".equals(p) || "ENTERPRISE".equals(p)) {
                            planOrPriceId = p;
                        } else {
                            Map<String, String> error = new HashMap<>();
                            error.put("error", "Invalid plan. Use BASIC, PRO, or ENTERPRISE.");
                            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
                        }
                    }
                }
                if (planOrPriceId == null && request.containsKey("priceId")) {
                    String priceId = request.get("priceId");
                    if (priceId == null || priceId.trim().isEmpty() || "invalid_price_id".equals(priceId)) {
                        Map<String, String> error = new HashMap<>();
                        error.put("error", "Invalid price ID");
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
                    }
                    String trimmed = priceId.trim();
                    if (!stripeService.isAllowedPriceId(trimmed)) {
                        Map<String, String> error = new HashMap<>();
                        error.put("error", "Price ID not allowed");
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
                    }
                    planOrPriceId = trimmed;
                }
            }

            // Check if user already has an active subscription
            Optional<Subscription> existingSubscription = subscriptionRepository.findByUserId(user.getId());
            if (existingSubscription.isPresent() && existingSubscription.get().canUseChatbot()) {
                Map<String, String> response = new HashMap<>();
                response.put("error", "User already has an active subscription");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            if (!stripeService.isConfigured()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Payment provider not configured");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
            }

            String checkoutUrl = stripeService.createCheckoutSession(user, planOrPriceId);

            Map<String, String> response = new HashMap<>();
            response.put("checkoutUrl", checkoutUrl);

            logger.info("Created checkout session for user: {}", LogSanitizer.sanitize(user.getEmail()));
            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Payment provider not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
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
     * Create a Stripe Customer Billing Portal session (manage subscription, payment method, invoices).
     */
    @PostMapping("/create-portal-session")
    public ResponseEntity<Map<String, String>> createPortalSession(
            @AuthenticationPrincipal CustomOAuth2User currentUser,
            @RequestBody(required = false) Map<String, String> request) {

        try {
            User user = currentUser.getUser();
            if (!stripeService.isConfigured()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Payment provider not configured");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
            }
            String returnUrl = request != null && request.containsKey("returnUrl")
                ? request.get("returnUrl") : null;
            if (returnUrl != null && !returnUrl.isBlank() && !isAllowedReturnUrl(returnUrl)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid return URL");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            String portalUrl = stripeService.createBillingPortalSession(user, returnUrl);
            Map<String, String> response = new HashMap<>();
            response.put("portalUrl", portalUrl);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Payment provider not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
        } catch (StripeException e) {
            logger.error("Stripe error creating portal session: {}", LogSanitizer.sanitizeException(e));
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to open billing portal");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        } catch (Exception e) {
            logger.error("Error creating portal session: {}", LogSanitizer.sanitizeException(e));
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /** Max length for return URL (Stripe limit 500; we enforce to prevent abuse). */
    private static final int MAX_RETURN_URL_LENGTH = 500;

    /**
     * Validate return URL to prevent open-redirect: only https (or http for localhost), host must exactly match an allowed origin, no javascript/data, length capped.
     */
    private boolean isAllowedReturnUrl(String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank() || returnUrl.length() > MAX_RETURN_URL_LENGTH) {
            return false;
        }
        String url = returnUrl.trim();
        String lower = url.toLowerCase();
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:") || lower.startsWith("file:")) {
            return false;
        }
        java.net.URI uri;
        try {
            uri = java.net.URI.create(url);
        } catch (Exception e) {
            return false;
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isEmpty()) return false;
        String scheme = uri.getScheme();
        if (scheme == null || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) return false;
        if (!isValidHostForRedirect(host)) return false;
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return ("http".equalsIgnoreCase(scheme) && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)));
        }
        for (String origin : allowedOrigins.split(",")) {
            String base = origin.trim().replace("\r", "").replace("\n", "");
            if (base.isEmpty()) continue;
            try {
                java.net.URI baseUri = java.net.URI.create(base);
                String baseHost = baseUri.getHost();
                if (baseHost == null || !baseHost.equalsIgnoreCase(host)) continue;
                String basePath = baseUri.getPath();
                if (basePath == null) basePath = "";
                String path = uri.getPath();
                if (path == null) path = "";
                if (basePath.isEmpty() || basePath.equals("/")) return true;
                if (path.equals(basePath) || path.startsWith(basePath + "/")) return true;
            } catch (Exception ignored) {
                continue;
            }
        }
        return false;
    }

    private static boolean isValidHostForRedirect(String host) {
        if (host == null || host.isEmpty()) return false;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c <= 32 || c >= 127) return false;
            if (c == '/' || c == '\\' || c == '@' || c == '?' || c == '#' || c == '[' || c == ']') return false;
        }
        return true;
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
     * Get subscription details (safe fields only; Stripe customer/subscription/price IDs are not exposed).
     */
    @GetMapping("/details")
    public ResponseEntity<Map<String, Object>> getSubscriptionDetails(
            @AuthenticationPrincipal CustomOAuth2User currentUser) {

        try {
            User user = currentUser.getUser();
            Optional<Subscription> subscriptionOpt = subscriptionRepository.findByUserId(user.getId());

            if (subscriptionOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Subscription sub = subscriptionOpt.get();
            Map<String, Object> safe = new HashMap<>();
            safe.put("id", sub.getId());
            safe.put("status", sub.getStatus());
            safe.put("plan", sub.getPlan());
            safe.put("isActive", sub.isActive());
            safe.put("canUseChatbot", sub.canUseChatbot());
            safe.put("currentPeriodStart", sub.getCurrentPeriodStart());
            safe.put("currentPeriodEnd", sub.getCurrentPeriodEnd());
            safe.put("canceledAt", sub.getCanceledAt());
            safe.put("createdAt", sub.getCreatedAt());
            safe.put("updatedAt", sub.getUpdatedAt());
            return ResponseEntity.ok(safe);

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
            @RequestBody(required = false) Map<String, String> request) {

        try {
            if (request == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Missing request body");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
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
            if (newPlan == Subscription.SubscriptionPlan.FREE) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Use the cancel endpoint to stop your subscription. FREE is not a selectable plan for change.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            if (!stripeService.isAllowedPriceId(newPriceId)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Price ID not allowed");
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
            @RequestBody(required = false) Map<String, String> request) {

        try {
            if (request == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Missing request body");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
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
            if (newPlan == Subscription.SubscriptionPlan.FREE) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "FREE is not a valid target for upgrade");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            if (!stripeService.isAllowedPriceId(newPriceId)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Price ID not allowed");
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
            @RequestBody(required = false) Map<String, String> request) {

        try {
            if (request == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Missing request body");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
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
            if (newPlan == Subscription.SubscriptionPlan.FREE) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Use the cancel endpoint to stop your subscription. FREE is not a valid target for downgrade.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            if (!stripeService.isAllowedPriceId(newPriceId)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Price ID not allowed");
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
