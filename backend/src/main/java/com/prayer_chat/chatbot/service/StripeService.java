package com.prayer_chat.chatbot.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import com.prayer_chat.chatbot.util.LogSanitizer;
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

    @Value("${stripe.price-id-basic:}")
    private String stripePriceIdBasic;

    @Value("${stripe.price-id-pro:}")
    private String stripePriceIdPro;

    @Value("${stripe.price-id-enterprise:}")
    private String stripePriceIdEnterprise;

    @Value("${stripe.price-amount:498}")
    private Long priceAmount;

    @Value("${stripe.price-currency:usd}")
    private String priceCurrency;

    @Value("${stripe.product-name:Prayer-Chat Monthly Subscription}")
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
     * Whether Stripe is configured (secret key set). When false, checkout and other payment APIs should return a clear error.
     */
    public boolean isConfigured() {
        return stripeApiKey != null && !stripeApiKey.trim().isEmpty();
    }

    /**
     * Create a Stripe checkout session for subscription.
     * @param user the user
     * @param planOrPriceId optional: plan name (BASIC, PRO, ENTERPRISE) or Stripe price ID (price_xxx). If null/empty, uses default price.
     * @throws IllegalStateException if Stripe is not configured (missing STRIPE_SECRET_KEY)
     */
    public String createCheckoutSession(User user, String planOrPriceId) throws StripeException {
        if (!isConfigured()) {
            throw new IllegalStateException("Stripe is not configured. Set STRIPE_SECRET_KEY to enable payments.");
        }
        String customerId = getOrCreateCustomer(user);
        String effectivePriceId = resolvePriceId(planOrPriceId);
        String planForMetadata = planOrPriceId != null && !planOrPriceId.isEmpty()
            && (Subscription.SubscriptionPlan.BASIC.name().equalsIgnoreCase(planOrPriceId)
                || Subscription.SubscriptionPlan.PRO.name().equalsIgnoreCase(planOrPriceId)
                || Subscription.SubscriptionPlan.ENTERPRISE.name().equalsIgnoreCase(planOrPriceId))
            ? planOrPriceId.toUpperCase() : null;

        SessionCreateParams.LineItem.Builder lineItemBuilder = SessionCreateParams.LineItem.builder()
            .setQuantity(1L);

        if (effectivePriceId != null && !effectivePriceId.isEmpty()) {
            lineItemBuilder.setPrice(effectivePriceId);
            logger.info("Using Stripe Price ID: {} for plan/price: {}", effectivePriceId, planOrPriceId);
        } else {
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

        SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setCustomer(customerId)
            .addLineItem(lineItemBuilder.build())
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .setClientReferenceId("user_" + user.getId())
            .putMetadata("user_id", user.getId().toString());
        if (planForMetadata != null) {
            paramsBuilder.putMetadata("plan", planForMetadata);
        }
        // Idempotency key: same user+plan within 5 min returns same session (retries/double-clicks). Max 255 chars for Stripe.
        String planPart = (planOrPriceId != null && planOrPriceId.length() <= 100) ? planOrPriceId : "default";
        String idemKey = "ck_" + user.getId() + "_" + planPart + "_" + (System.currentTimeMillis() / 300_000L);
        if (idemKey.length() > 255) {
            idemKey = idemKey.substring(0, 255);
        }
        RequestOptions requestOptions = RequestOptions.builder().setIdempotencyKey(idemKey).build();
        Session session = Session.create(paramsBuilder.build(), requestOptions);
        logger.info("Created Stripe checkout session for user: {}", LogSanitizer.sanitize(user.getEmail()));
        return session.getUrl();
    }

    /**
     * Map Stripe price ID to subscription plan (for webhook handling).
     */
    private Subscription.SubscriptionPlan planFromPriceId(String priceId) {
        if (priceId == null) return Subscription.SubscriptionPlan.BASIC;
        if (stripePriceIdBasic != null && priceId.equals(stripePriceIdBasic)) return Subscription.SubscriptionPlan.BASIC;
        if (stripePriceIdPro != null && priceId.equals(stripePriceIdPro)) return Subscription.SubscriptionPlan.PRO;
        if (stripePriceIdEnterprise != null && priceId.equals(stripePriceIdEnterprise)) return Subscription.SubscriptionPlan.ENTERPRISE;
        if (stripePriceId != null && priceId.equals(stripePriceId)) return Subscription.SubscriptionPlan.BASIC; // default price = BASIC
        return Subscription.SubscriptionPlan.BASIC;
    }

    /**
     * Whether the given Stripe price ID is one of our configured prices (security: prevent arbitrary price subscription).
     */
    public boolean isAllowedPriceId(String priceId) {
        if (priceId == null || priceId.isBlank()) return false;
        String p = priceId.trim();
        return p.equals(stripePriceId) || p.equals(stripePriceIdBasic) || p.equals(stripePriceIdPro) || p.equals(stripePriceIdEnterprise);
    }

    /**
     * Resolve plan name or price ID to a Stripe price ID. Returns null if fallback to default inline price should be used.
     * Security: raw price_ IDs are only accepted if they are in our configured list.
     */
    private String resolvePriceId(String planOrPriceId) {
        if (planOrPriceId == null || planOrPriceId.trim().isEmpty()) {
            return stripePriceId != null && !stripePriceId.isEmpty() ? stripePriceId : null;
        }
        String s = planOrPriceId.trim();
        if (s.startsWith("price_")) {
            return isAllowedPriceId(s) ? s : (stripePriceId != null && !stripePriceId.isEmpty() ? stripePriceId : null);
        }
        return switch (s.toUpperCase()) {
            case "BASIC" -> stripePriceIdBasic != null && !stripePriceIdBasic.isEmpty() ? stripePriceIdBasic : stripePriceId;
            case "PRO" -> stripePriceIdPro != null && !stripePriceIdPro.isEmpty() ? stripePriceIdPro : stripePriceId;
            case "ENTERPRISE" -> stripePriceIdEnterprise != null && !stripePriceIdEnterprise.isEmpty() ? stripePriceIdEnterprise : stripePriceId;
            default -> stripePriceId != null && !stripePriceId.isEmpty() ? stripePriceId : null;
        };
    }

    /**
     * Create a Stripe Customer Billing Portal session so the user can manage subscription, payment method, invoices.
     * @param user current user (must have Stripe customer ID)
     * @param returnUrl URL to redirect to when user leaves the portal (e.g. dashboard)
     * @return portal URL to redirect the user to
     */
    public String createBillingPortalSession(User user, String returnUrl) throws StripeException {
        if (!isConfigured()) {
            throw new IllegalStateException("Stripe is not configured. Set STRIPE_SECRET_KEY to enable payments.");
        }
        String customerId = getOrCreateCustomer(user);
        com.stripe.param.billingportal.SessionCreateParams portalParams =
            com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(customerId)
                .setReturnUrl(returnUrl != null && !returnUrl.isEmpty() ? returnUrl : successUrl)
                .build();
        // Idempotency key: same user within 5 min returns same portal session (retries/double-clicks)
        String idemKey = "portal_" + user.getId() + "_" + (System.currentTimeMillis() / 300_000L);
        RequestOptions requestOptions = RequestOptions.builder().setIdempotencyKey(idemKey).build();
        com.stripe.model.billingportal.Session portalSession =
            com.stripe.model.billingportal.Session.create(portalParams, requestOptions);
        logger.info("Created billing portal session for user: {}", LogSanitizer.sanitize(user.getEmail()));
        return portalSession.getUrl();
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
        logger.info("Created Stripe customer for user: {}", LogSanitizer.sanitize(user.getEmail()));

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
        List<com.stripe.model.SubscriptionItem> items = stripeSubscription.getItems().getData();
        if (items == null || items.isEmpty()) {
            logger.error("Stripe subscription {} has no items", stripeSubscription.getId());
            return;
        }
        com.stripe.model.SubscriptionItem firstItem = items.get(0);
        String priceId = firstItem.getPrice().getId();
        subscription.setStripeSubscriptionId(stripeSubscription.getId());
        subscription.setStripePriceId(priceId);
        subscription.setStatus(mapStripeStatus(stripeSubscription.getStatus()));
        subscription.setPlan(planFromPriceId(priceId));
        subscription.setCurrentPeriodStart(convertToLocalDateTime(firstItem.getCurrentPeriodStart()));
        subscription.setCurrentPeriodEnd(convertToLocalDateTime(firstItem.getCurrentPeriodEnd()));

        subscriptionRepository.save(subscription);
        logger.info("Subscription created for user: {}", LogSanitizer.sanitize(subscription.getUser().getEmail()));
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
        // Get billing periods from subscription item (Stripe API 2025-03-31+)
        if (!stripeSubscription.getItems().getData().isEmpty()) {
            com.stripe.model.SubscriptionItem firstItem = stripeSubscription.getItems().getData().get(0);
            subscription.setCurrentPeriodStart(convertToLocalDateTime(firstItem.getCurrentPeriodStart()));
            subscription.setCurrentPeriodEnd(convertToLocalDateTime(firstItem.getCurrentPeriodEnd()));
        }

        if (stripeSubscription.getCanceledAt() != null) {
            subscription.setCanceledAt(convertToLocalDateTime(stripeSubscription.getCanceledAt()));
        }

        subscriptionRepository.save(subscription);
        logger.info("Subscription updated for user: {}", LogSanitizer.sanitize(subscription.getUser().getEmail()));
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
        logger.info("Subscription canceled for user: {}", LogSanitizer.sanitize(subscription.getUser().getEmail()));
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
        if (!isAllowedPriceId(newPriceId)) {
            throw new IllegalArgumentException("Invalid or disallowed price ID for upgrade");
        }
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

        List<com.stripe.model.SubscriptionItem> subscriptionItems = stripeSubscription.getItems().getData();
        if (subscriptionItems == null || subscriptionItems.isEmpty()) {
            throw new IllegalStateException("Stripe subscription has no items");
        }
        String subscriptionItemId = subscriptionItems.get(0).getId();

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
        if (!isAllowedPriceId(newPriceId)) {
            throw new IllegalArgumentException("Invalid or disallowed price ID for downgrade");
        }
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

        List<com.stripe.model.SubscriptionItem> subscriptionItems = stripeSubscription.getItems().getData();
        if (subscriptionItems == null || subscriptionItems.isEmpty()) {
            throw new IllegalStateException("Stripe subscription has no items");
        }
        String subscriptionItemId = subscriptionItems.get(0).getId();

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
     * Change subscription plan (auto-detect upgrade vs downgrade).
     * Rejects FREE (use cancel instead) and ensures priceId matches the requested plan.
     */
    public void changeSubscriptionPlan(Long userId, String newPriceId, Subscription.SubscriptionPlan newPlan) throws StripeException {
        if (newPlan == Subscription.SubscriptionPlan.FREE) {
            throw new IllegalArgumentException("To cancel your plan, use the cancel endpoint. FREE is not a changeable plan.");
        }
        if (!isAllowedPriceId(newPriceId)) {
            throw new IllegalArgumentException("Invalid or disallowed price ID for plan change");
        }
        Subscription.SubscriptionPlan planForPrice = planFromPriceId(newPriceId);
        if (planForPrice != newPlan) {
            throw new IllegalArgumentException("Price ID does not match plan: " + newPlan);
        }

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
