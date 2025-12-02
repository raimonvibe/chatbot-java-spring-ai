package com.tjanabot.chatbot.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.tjanabot.chatbot.model.Subscription;
import com.tjanabot.chatbot.model.User;
import com.tjanabot.chatbot.repository.SubscriptionRepository;
import com.tjanabot.chatbot.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for handling Stripe payment processing and subscriptions
 */
@Service
public class StripeService {

    private static final Logger logger = LoggerFactory.getLogger(StripeService.class);

    @Value("${stripe.api-key:}")
    private String stripeApiKey;

    @Value("${stripe.price-id:}")
    private String stripePriceId;

    @Value("${stripe.price-amount:498}")
    private Long priceAmount;

    @Value("${stripe.price-currency:usd}")
    private String priceCurrency;

    @Value("${stripe.product-name:TjanaBot Monthly Subscription}")
    private String productName;

    @Value("${stripe.success-url:http://localhost:3000/dashboard}")
    private String successUrl;

    @Value("${stripe.cancel-url:http://localhost:3000/pricing}")
    private String cancelUrl;

    @Value("${stripe.grace-period-days:7}")
    private int gracePeriodDays;

    @Value("${stripe.max-retry-attempts:3}")
    private int maxRetryAttempts;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @PostConstruct
    public void init() {
        if (stripeApiKey != null && !stripeApiKey.isEmpty()) {
            Stripe.apiKey = stripeApiKey;
            logger.info("Stripe API initialized");
        } else {
            logger.warn("Stripe API key not configured - payment features will be disabled");
        }
    }

    /**
     * Create a Stripe checkout session for subscription
     */
    public String createCheckoutSession(User user) throws StripeException {
        // Get or create Stripe customer
        String customerId = getOrCreateCustomer(user);

        // Build line item - use Price ID if available, otherwise use default price
        SessionCreateParams.LineItem.Builder lineItemBuilder = SessionCreateParams.LineItem.builder()
            .setQuantity(1L);

        if (stripePriceId != null && !stripePriceId.isEmpty()) {
            // Use existing Stripe Price ID
            lineItemBuilder.setPrice(stripePriceId);
            logger.info("Using Stripe Price ID: {}", stripePriceId);
        } else {
            // Use default inline price
            lineItemBuilder.setPriceData(
                SessionCreateParams.LineItem.PriceData.builder()
                    .setCurrency(priceCurrency)
                    .setUnitAmount(priceAmount)
                    .setRecurring(
                        SessionCreateParams.LineItem.PriceData.Recurring.builder()
                            .setInterval(SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH)
                            .build()
                    )
                    .setProductData(
                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                            .setName(productName)
                            .build()
                    )
                    .build()
            );
            logger.info("Using default price: ${}{} per month", priceAmount / 100.0, priceCurrency.toUpperCase());
        }

        // Create checkout session
        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setCustomer(customerId)
            .addLineItem(lineItemBuilder.build())
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .putMetadata("user_id", user.getId().toString())
            .build();

        Session session = Session.create(params);
        logger.info("Created Stripe checkout session for user: {}", user.getEmail());

        return session.getUrl();
    }

    /**
     * Get existing Stripe customer or create a new one
     */
    private String getOrCreateCustomer(User user) throws StripeException {
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByUserId(user.getId());

        if (subscriptionOpt.isPresent() && subscriptionOpt.get().getStripeCustomerId() != null) {
            return subscriptionOpt.get().getStripeCustomerId();
        }

        // Create new Stripe customer
        CustomerCreateParams params = CustomerCreateParams.builder()
            .setEmail(user.getEmail())
            .setName(user.getUsername())
            .putMetadata("user_id", user.getId().toString())
            .build();

        Customer customer = Customer.create(params);
        logger.info("Created Stripe customer for user: {}", user.getEmail());

        // Save customer ID to subscription
        Subscription subscription = subscriptionOpt.orElse(new Subscription(user, customer.getId()));
        subscription.setStripeCustomerId(customer.getId());
        subscriptionRepository.save(subscription);

        return customer.getId();
    }

    /**
     * Handle successful subscription creation
     */
    public void handleSubscriptionCreated(com.stripe.model.Subscription stripeSubscription) {
        String customerId = stripeSubscription.getCustomer();
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByStripeCustomerId(customerId);

        if (subscriptionOpt.isEmpty()) {
            logger.error("No subscription found for Stripe customer: {}", customerId);
            return;
        }

        Subscription subscription = subscriptionOpt.get();
        subscription.setStripeSubscriptionId(stripeSubscription.getId());
        subscription.setStripePriceId(stripeSubscription.getItems().getData().get(0).getPrice().getId());
        subscription.setStatus(mapStripeStatus(stripeSubscription.getStatus()));
        subscription.setPlan(Subscription.SubscriptionPlan.BASIC);  // Default plan
        subscription.setCurrentPeriodStart(convertToLocalDateTime(stripeSubscription.getCurrentPeriodStart()));
        subscription.setCurrentPeriodEnd(convertToLocalDateTime(stripeSubscription.getCurrentPeriodEnd()));

        subscriptionRepository.save(subscription);
        logger.info("Subscription created for user: {}", subscription.getUser().getEmail());
    }

    /**
     * Handle subscription update
     */
    public void handleSubscriptionUpdated(com.stripe.model.Subscription stripeSubscription) {
        Optional<Subscription> subscriptionOpt = subscriptionRepository
            .findByStripeSubscriptionId(stripeSubscription.getId());

        if (subscriptionOpt.isEmpty()) {
            logger.error("No subscription found for Stripe subscription: {}", stripeSubscription.getId());
            return;
        }

        Subscription subscription = subscriptionOpt.get();
        subscription.setStatus(mapStripeStatus(stripeSubscription.getStatus()));
        subscription.setCurrentPeriodStart(convertToLocalDateTime(stripeSubscription.getCurrentPeriodStart()));
        subscription.setCurrentPeriodEnd(convertToLocalDateTime(stripeSubscription.getCurrentPeriodEnd()));

        if (stripeSubscription.getCanceledAt() != null) {
            subscription.setCanceledAt(convertToLocalDateTime(stripeSubscription.getCanceledAt()));
        }

        subscriptionRepository.save(subscription);
        logger.info("Subscription updated for user: {}", subscription.getUser().getEmail());
    }

    /**
     * Handle subscription deletion/cancellation
     */
    public void handleSubscriptionDeleted(com.stripe.model.Subscription stripeSubscription) {
        Optional<Subscription> subscriptionOpt = subscriptionRepository
            .findByStripeSubscriptionId(stripeSubscription.getId());

        if (subscriptionOpt.isEmpty()) {
            logger.error("No subscription found for Stripe subscription: {}", stripeSubscription.getId());
            return;
        }

        Subscription subscription = subscriptionOpt.get();
        subscription.setStatus(Subscription.SubscriptionStatus.CANCELED);
        subscription.setCanceledAt(LocalDateTime.now());

        subscriptionRepository.save(subscription);
        logger.info("Subscription canceled for user: {}", subscription.getUser().getEmail());
    }

    /**
     * Cancel a subscription
     */
    public void cancelSubscription(Long userId) throws StripeException {
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByUserId(userId);

        if (subscriptionOpt.isEmpty() || subscriptionOpt.get().getStripeSubscriptionId() == null) {
            throw new IllegalArgumentException("No active subscription found for user");
        }

        Subscription subscription = subscriptionOpt.get();
        com.stripe.model.Subscription stripeSubscription =
            com.stripe.model.Subscription.retrieve(subscription.getStripeSubscriptionId());
        stripeSubscription.cancel();

        subscription.setStatus(Subscription.SubscriptionStatus.CANCELED);
        subscription.setCanceledAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);

        logger.info("Canceled subscription for user ID: {}", userId);
    }

    /**
     * Upgrade subscription to a higher plan
     */
    public void upgradeSubscription(Long userId, String newPriceId, Subscription.SubscriptionPlan newPlan) throws StripeException {
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByUserId(userId);

        if (subscriptionOpt.isEmpty() || subscriptionOpt.get().getStripeSubscriptionId() == null) {
            throw new IllegalArgumentException("No active subscription found for user");
        }

        Subscription subscription = subscriptionOpt.get();

        // Validate upgrade (can only upgrade to higher tier)
        if (!isUpgrade(subscription.getPlan(), newPlan)) {
            throw new IllegalArgumentException("Invalid upgrade: " + subscription.getPlan() + " to " + newPlan);
        }

        // Update Stripe subscription
        com.stripe.model.Subscription stripeSubscription =
            com.stripe.model.Subscription.retrieve(subscription.getStripeSubscriptionId());

        // Get the subscription item ID
        String subscriptionItemId = stripeSubscription.getItems().getData().get(0).getId();

        // Update the subscription with new price
        Map<String, Object> items = new HashMap<>();
        items.put("id", subscriptionItemId);
        items.put("price", newPriceId);

        Map<String, Object> params = new HashMap<>();
        params.put("items", List.of(items));
        params.put("proration_behavior", "always_invoice"); // Charge prorated amount immediately

        stripeSubscription.update(params);

        // Update local subscription
        subscription.setPlan(newPlan);
        subscription.setStripePriceId(newPriceId);
        subscriptionRepository.save(subscription);

        logger.info("Upgraded subscription for user ID {} to plan: {}", userId, newPlan);
    }

    /**
     * Downgrade subscription to a lower plan
     */
    public void downgradeSubscription(Long userId, String newPriceId, Subscription.SubscriptionPlan newPlan) throws StripeException {
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByUserId(userId);

        if (subscriptionOpt.isEmpty() || subscriptionOpt.get().getStripeSubscriptionId() == null) {
            throw new IllegalArgumentException("No active subscription found for user");
        }

        Subscription subscription = subscriptionOpt.get();

        // Validate downgrade (can only downgrade to lower tier)
        if (!isDowngrade(subscription.getPlan(), newPlan)) {
            throw new IllegalArgumentException("Invalid downgrade: " + subscription.getPlan() + " to " + newPlan);
        }

        // Update Stripe subscription
        com.stripe.model.Subscription stripeSubscription =
            com.stripe.model.Subscription.retrieve(subscription.getStripeSubscriptionId());

        // Get the subscription item ID
        String subscriptionItemId = stripeSubscription.getItems().getData().get(0).getId();

        // Update the subscription with new price
        Map<String, Object> items = new HashMap<>();
        items.put("id", subscriptionItemId);
        items.put("price", newPriceId);

        Map<String, Object> params = new HashMap<>();
        params.put("items", List.of(items));
        params.put("proration_behavior", "none"); // Don't prorate for downgrades, apply at end of period

        stripeSubscription.update(params);

        // Update local subscription
        subscription.setPlan(newPlan);
        subscription.setStripePriceId(newPriceId);
        subscriptionRepository.save(subscription);

        logger.info("Downgraded subscription for user ID {} to plan: {}", userId, newPlan);
    }

    /**
     * Change subscription plan (auto-detect upgrade vs downgrade)
     */
    public void changeSubscriptionPlan(Long userId, String newPriceId, Subscription.SubscriptionPlan newPlan) throws StripeException {
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByUserId(userId);

        if (subscriptionOpt.isEmpty()) {
            throw new IllegalArgumentException("No subscription found for user");
        }

        Subscription subscription = subscriptionOpt.get();
        Subscription.SubscriptionPlan currentPlan = subscription.getPlan();

        if (currentPlan == newPlan) {
            throw new IllegalArgumentException("User is already on the " + newPlan + " plan");
        }

        if (isUpgrade(currentPlan, newPlan)) {
            upgradeSubscription(userId, newPriceId, newPlan);
        } else if (isDowngrade(currentPlan, newPlan)) {
            downgradeSubscription(userId, newPriceId, newPlan);
        } else {
            throw new IllegalArgumentException("Invalid plan change from " + currentPlan + " to " + newPlan);
        }
    }

    /**
     * Check if plan change is an upgrade
     */
    private boolean isUpgrade(Subscription.SubscriptionPlan current, Subscription.SubscriptionPlan target) {
        int currentTier = getPlanTier(current);
        int targetTier = getPlanTier(target);
        return targetTier > currentTier;
    }

    /**
     * Check if plan change is a downgrade
     */
    private boolean isDowngrade(Subscription.SubscriptionPlan current, Subscription.SubscriptionPlan target) {
        int currentTier = getPlanTier(current);
        int targetTier = getPlanTier(target);
        return targetTier < currentTier;
    }

    /**
     * Get numeric tier for plan comparison
     */
    private int getPlanTier(Subscription.SubscriptionPlan plan) {
        return switch (plan) {
            case FREE -> 0;
            case BASIC -> 1;
            case PRO -> 2;
            case ENTERPRISE -> 3;
        };
    }

    /**
     * Handle payment failure with grace period and retry logic
     */
    public void handlePaymentFailure(String stripeSubscriptionId, String invoiceId) {
        Optional<Subscription> subscriptionOpt = subscriptionRepository
            .findByStripeSubscriptionId(stripeSubscriptionId);

        if (subscriptionOpt.isEmpty()) {
            logger.error("No subscription found for Stripe subscription: {}", stripeSubscriptionId);
            return;
        }

        Subscription subscription = subscriptionOpt.get();

        // Increment retry count
        int currentRetryCount = subscription.getPaymentRetryCount() != null ?
            subscription.getPaymentRetryCount() : 0;
        subscription.setPaymentRetryCount(currentRetryCount + 1);
        subscription.setLastPaymentAttempt(LocalDateTime.now());

        // Set grace period if first failure
        if (currentRetryCount == 0 && subscription.getGracePeriodEnd() == null) {
            LocalDateTime gracePeriodEnd = LocalDateTime.now().plusDays(gracePeriodDays);
            subscription.setGracePeriodEnd(gracePeriodEnd);
            logger.info("Grace period set until {} for subscription: {}",
                gracePeriodEnd, stripeSubscriptionId);
        }

        // Check if max retries exceeded or grace period expired
        if (subscription.getPaymentRetryCount() >= maxRetryAttempts ||
            (subscription.getGracePeriodEnd() != null &&
             LocalDateTime.now().isAfter(subscription.getGracePeriodEnd()))) {

            // Revoke access after grace period or max retries
            subscription.setStatus(Subscription.SubscriptionStatus.UNPAID);
            logger.warn("Payment failed {} times for subscription {}, access revoked",
                subscription.getPaymentRetryCount(), stripeSubscriptionId);
        } else {
            // Still in grace period or retry window
            subscription.setStatus(Subscription.SubscriptionStatus.PAST_DUE);
            logger.warn("Payment failed (attempt {}/{}) for subscription {}",
                subscription.getPaymentRetryCount(), maxRetryAttempts, stripeSubscriptionId);
        }

        subscriptionRepository.save(subscription);
    }

    /**
     * Handle successful payment (reset retry counters)
     */
    public void handlePaymentSuccess(String stripeSubscriptionId) {
        Optional<Subscription> subscriptionOpt = subscriptionRepository
            .findByStripeSubscriptionId(stripeSubscriptionId);

        if (subscriptionOpt.isEmpty()) {
            logger.error("No subscription found for Stripe subscription: {}", stripeSubscriptionId);
            return;
        }

        Subscription subscription = subscriptionOpt.get();

        // Reset retry counters and grace period
        subscription.setPaymentRetryCount(0);
        subscription.setLastPaymentAttempt(null);
        subscription.setGracePeriodEnd(null);
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);

        subscriptionRepository.save(subscription);
        logger.info("Payment succeeded for subscription {}, retry counters reset", stripeSubscriptionId);
    }

    /**
     * Check if subscription is in grace period
     */
    public boolean isInGracePeriod(Long userId) {
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByUserId(userId);

        if (subscriptionOpt.isEmpty()) {
            return false;
        }

        Subscription subscription = subscriptionOpt.get();
        LocalDateTime gracePeriodEnd = subscription.getGracePeriodEnd();

        return gracePeriodEnd != null && LocalDateTime.now().isBefore(gracePeriodEnd);
    }

    /**
     * Get remaining grace period days
     */
    public int getRemainingGracePeriodDays(Long userId) {
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByUserId(userId);

        if (subscriptionOpt.isEmpty() || subscriptionOpt.get().getGracePeriodEnd() == null) {
            return 0;
        }

        Subscription subscription = subscriptionOpt.get();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime gracePeriodEnd = subscription.getGracePeriodEnd();

        if (now.isAfter(gracePeriodEnd)) {
            return 0;
        }

        return (int) java.time.temporal.ChronoUnit.DAYS.between(now, gracePeriodEnd);
    }

    /**
     * Map Stripe subscription status to our enum
     */
    private Subscription.SubscriptionStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "active" -> Subscription.SubscriptionStatus.ACTIVE;
            case "past_due" -> Subscription.SubscriptionStatus.PAST_DUE;
            case "canceled" -> Subscription.SubscriptionStatus.CANCELED;
            case "incomplete" -> Subscription.SubscriptionStatus.INCOMPLETE;
            case "incomplete_expired" -> Subscription.SubscriptionStatus.INCOMPLETE_EXPIRED;
            case "trialing" -> Subscription.SubscriptionStatus.TRIALING;
            case "unpaid" -> Subscription.SubscriptionStatus.UNPAID;
            default -> Subscription.SubscriptionStatus.INACTIVE;
        };
    }

    /**
     * Convert Unix timestamp to LocalDateTime
     */
    private LocalDateTime convertToLocalDateTime(Long timestamp) {
        if (timestamp == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
    }
}
