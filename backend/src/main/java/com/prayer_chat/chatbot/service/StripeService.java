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

    @Value("${stripe.success-url:http://localhost:3000/account?payment=success&session_id={CHECKOUT_SESSION_ID}}")
    private String successUrl;

    @Value("${stripe.cancel-url:http://localhost:3000/pricing}")
    private String cancelUrl;

    @Value("${stripe.allowed-redirect-origins:http://localhost:3000,https://prayer-chat.com,https://www.prayer-chat.com}")
    private String allowedRedirectOrigins;

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
            validateRedirectUrls();
        } else {
            logger.warn("Stripe API key not configured - payment features will be disabled");
        }
    }

    /**
     * Validates that success and cancel URLs belong to allowed redirect origins (prevents open redirect via misconfiguration).
     * @throws IllegalStateException if URLs are not allowed
     */
    private void validateRedirectUrls() {
        if (successUrl == null || successUrl.isBlank() || cancelUrl == null || cancelUrl.isBlank()) {
            throw new IllegalStateException("Stripe success-url and cancel-url must be set when Stripe is configured.");
        }
        String effectiveOrigins = (allowedRedirectOrigins == null || allowedRedirectOrigins.isBlank())
            ? "http://localhost:3000,https://prayer-chat.com,https://www.prayer-chat.com" : allowedRedirectOrigins.trim();
        if (!isAllowedRedirectUrl(successUrl, effectiveOrigins)) {
            String host = getHostFromUrl(successUrl);
            throw new IllegalStateException(
                "Stripe success-url host [" + host + "] is not in STRIPE_ALLOWED_REDIRECT_ORIGINS. "
                    + "Set STRIPE_ALLOWED_REDIRECT_ORIGINS to a comma-separated list including your app host (e.g. https://www.prayer-chat.com). "
                    + "Current allowed origins: " + effectiveOrigins);
        }
        if (!isAllowedRedirectUrl(cancelUrl, effectiveOrigins)) {
            String host = getHostFromUrl(cancelUrl);
            throw new IllegalStateException(
                "Stripe cancel-url host [" + host + "] is not in STRIPE_ALLOWED_REDIRECT_ORIGINS. "
                    + "Set STRIPE_ALLOWED_REDIRECT_ORIGINS to include your app host. Current allowed origins: " + effectiveOrigins);
        }
    }

    private static String getHostFromUrl(String url) {
        if (url == null || url.isBlank()) return "(empty)";
        String normalized = url.trim().replace("{CHECKOUT_SESSION_ID}", "cs_test");
        try {
            java.net.URI uri = java.net.URI.create(normalized);
            String host = uri.getHost();
            return host != null ? host : "(no host)";
        } catch (Exception e) {
            return "(invalid URL)";
        }
    }

    /**
     * Returns true if the given redirect URL (may contain {CHECKOUT_SESSION_ID}) has scheme and host in allowed origins.
     * Used at startup to validate success/cancel URLs; exposed for tests.
     */
    public boolean isAllowedRedirectUrl(String url) {
        return isAllowedRedirectUrl(url, allowedRedirectOrigins);
    }

    private static boolean isAllowedRedirectUrl(String url, String allowedOrigins) {
        if (url == null || url.isBlank()) return false;
        String normalized = url.trim().replace("{CHECKOUT_SESSION_ID}", "cs_test");
        String lower = normalized.toLowerCase();
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:") || lower.startsWith("file:")) {
            return false;
        }
        java.net.URI uri;
        try {
            uri = java.net.URI.create(normalized);
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
        if (!isValidHost(host)) return false;
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return "http".equalsIgnoreCase(scheme) && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host));
        }
        for (String origin : allowedOrigins.split(",")) {
            String base = origin.trim().replace("\r", "").replace("\n", "");
            if (base.isEmpty()) continue;
            try {
                java.net.URI baseUri = java.net.URI.create(base);
                String baseHost = baseUri.getHost();
                if (baseHost != null && baseHost.equalsIgnoreCase(host)) return true;
            } catch (Exception ignored) {
                // continue
            }
        }
        return false;
    }

    /** Reject hosts with control chars or unexpected content that could indicate bypass attempts. */
    private static boolean isValidHost(String host) {
        if (host == null || host.isEmpty()) return false;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c <= 32 || c >= 127) return false;
            if (c == '/' || c == '\\' || c == '@' || c == '?' || c == '#' || c == '[' || c == ']') return false;
        }
        return true;
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
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User and user ID are required for checkout.");
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
            logger.info("Using Stripe Price ID: {} for plan/price: {}", effectivePriceId,
                planOrPriceId != null && !planOrPriceId.isEmpty() ? planOrPriceId : "default");
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

        // Idempotency key: same user+plan within 5 min returns same session (retries/double-clicks). Max 255 chars for Stripe.
        String planPart = (planOrPriceId != null && planOrPriceId.length() <= 100) ? planOrPriceId : "default";
        String idemKey = "ck_" + user.getId() + "_" + planPart + "_" + (System.currentTimeMillis() / 300_000L);
        if (idemKey.length() > 255) {
            idemKey = idemKey.substring(0, 255);
        }
        RequestOptions requestOptions = RequestOptions.builder().setIdempotencyKey(idemKey).build();

        SessionCreateParams params = buildCheckoutParams(user, customerId, lineItemBuilder.build(), planForMetadata);
        try {
            Session session = Session.create(params, requestOptions);
            logger.info("Created Stripe checkout session for user: {}", LogSanitizer.sanitize(user.getEmail()));
            return session.getUrl();
        } catch (StripeException e) {
            if (isNoSuchCustomer(e)) {
                logger.warn("Stored Stripe customer invalid (e.g. Test/Live switch), creating new customer and retrying");
                clearStripeCustomerIdForUser(user);
                customerId = getOrCreateCustomer(user);
                params = buildCheckoutParams(user, customerId, lineItemBuilder.build(), planForMetadata);
                Session session = Session.create(params, requestOptions);
                logger.info("Created Stripe checkout session for user: {}", LogSanitizer.sanitize(user.getEmail()));
                return session.getUrl();
            }
            throw e;
        }
    }

    private SessionCreateParams buildCheckoutParams(User user, String customerId,
            SessionCreateParams.LineItem lineItem, String planForMetadata) {
        SessionCreateParams.Builder b = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setCustomer(customerId)
            .addLineItem(lineItem)
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .setClientReferenceId("user_" + user.getId())
            .putMetadata("user_id", user.getId().toString());
        if (planForMetadata != null) {
            b.putMetadata("plan", planForMetadata);
        }
        return b.build();
    }

    /**
     * Map Stripe price ID to subscription plan (for webhook handling).
     */
    private Optional<Subscription.SubscriptionPlan> planFromPriceId(String priceId) {
        if (priceId == null || priceId.isBlank()) return Optional.empty();
        if (stripePriceIdBasic != null && priceId.equals(stripePriceIdBasic)) return Optional.of(Subscription.SubscriptionPlan.BASIC);
        if (stripePriceIdPro != null && priceId.equals(stripePriceIdPro)) return Optional.of(Subscription.SubscriptionPlan.PRO);
        if (stripePriceIdEnterprise != null && priceId.equals(stripePriceIdEnterprise)) return Optional.of(Subscription.SubscriptionPlan.ENTERPRISE);
        if (stripePriceId != null && priceId.equals(stripePriceId)) return Optional.of(Subscription.SubscriptionPlan.BASIC);
        logger.warn("Unknown Stripe price ID {}, keeping existing plan", priceId);
        return Optional.empty();
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
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User and user ID are required for billing portal.");
        }
        String customerId = getOrCreateCustomer(user);
        String returnUrlVal = returnUrl != null && !returnUrl.isEmpty() ? returnUrl : successUrl;
        com.stripe.param.billingportal.SessionCreateParams portalParams =
            com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(customerId)
                .setReturnUrl(returnUrlVal)
                .build();
        // Idempotency key: same user within 5 min returns same portal session (retries/double-clicks)
        String idemKey = "portal_" + user.getId() + "_" + (System.currentTimeMillis() / 300_000L);
        RequestOptions requestOptions = RequestOptions.builder().setIdempotencyKey(idemKey).build();
        try {
            com.stripe.model.billingportal.Session portalSession =
                com.stripe.model.billingportal.Session.create(portalParams, requestOptions);
            logger.info("Created billing portal session for user: {}", LogSanitizer.sanitize(user.getEmail()));
            return portalSession.getUrl();
        } catch (StripeException e) {
            if (isNoSuchCustomer(e)) {
                logger.warn("Stored Stripe customer invalid (e.g. Test/Live switch), creating new customer and retrying");
                clearStripeCustomerIdForUser(user);
                customerId = getOrCreateCustomer(user);
                portalParams = com.stripe.param.billingportal.SessionCreateParams.builder()
                    .setCustomer(customerId)
                    .setReturnUrl(returnUrlVal)
                    .build();
                com.stripe.model.billingportal.Session portalSession =
                    com.stripe.model.billingportal.Session.create(portalParams, requestOptions);
                logger.info("Created billing portal session for user: {}", LogSanitizer.sanitize(user.getEmail()));
                return portalSession.getUrl();
            }
            throw e;
        }
    }

    /**
     * Get existing Stripe customer or create a new one
     */
    private String getOrCreateCustomer(User user) throws StripeException {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User and user ID are required.");
        }
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
     * Clear stored Stripe customer ID for user (e.g. after Test/Live switch so next request creates a new customer).
     */
    private void clearStripeCustomerIdForUser(User user) {
        if (user == null || user.getId() == null) {
            logger.warn("Cannot clear Stripe customer ID: user or user ID is null");
            return;
        }
        subscriptionRepository.findByUserId(user.getId()).ifPresent(sub -> {
            sub.setStripeCustomerId(null);
            subscriptionRepository.save(sub);
            logger.info("Cleared invalid Stripe customer ID for user: {}", LogSanitizer.sanitize(user.getEmail()));
        });
    }

    /**
     * True if Stripe error indicates the customer ID does not exist (e.g. wrong Test/Live mode or account).
     */
    private static boolean isNoSuchCustomer(StripeException e) {
        if (e == null) return false;
        String code = e.getCode();
        String msg = e.getMessage() != null ? e.getMessage() : "";
        return "resource_missing".equals(code) && msg.contains("No such customer");
    }

    /** Stripe subscription ID format (sub_xxx) to avoid passing arbitrary strings to Stripe.retrieve(). */
    private static final java.util.regex.Pattern STRIPE_SUBSCRIPTION_ID = java.util.regex.Pattern.compile("^sub_[a-zA-Z0-9]{14,}$");

    /**
     * Stripe Checkout Session ID format (cs_test_... or cs_live_...). Per Stripe docs, IDs are alphanumeric + underscore.
     * Restricts input to prevent injection and arbitrary API calls; min length 20 after prefix to match real IDs.
     */
    private static final java.util.regex.Pattern STRIPE_CHECKOUT_SESSION_ID = java.util.regex.Pattern.compile("^cs_[a-zA-Z0-9_]{20,255}$");

    /**
     * Sync subscription from a checkout session ID (e.g. when user lands on account page with session_id after payment).
     * Validates that the session belongs to the given user via client_reference_id (Stripe best practice: verify ownership
     * server-side after retrieve). Use when webhook hasn't run yet.
     *
     * @param sessionId Stripe checkout session ID (cs_xxx)
     * @param userId    current user ID — must match session's client_reference_id ("user_" + userId)
     * @return true if subscription was synced, false if session has no subscription or mode is not subscription
     * @throws StripeException        if Stripe API call fails (e.g. invalid session ID)
     * @throws IllegalArgumentException if session does not belong to this user or session ID format is invalid
     */
    public boolean syncSubscriptionFromCheckoutSession(String sessionId, Long userId) throws StripeException {
        if (sessionId == null || sessionId.isBlank() || userId == null) {
            throw new IllegalArgumentException("session_id and user ID are required");
        }
        String sid = sessionId.trim();
        if (sid.length() > 255) {
            throw new IllegalArgumentException("Invalid session ID format");
        }
        if (!STRIPE_CHECKOUT_SESSION_ID.matcher(sid).matches()) {
            throw new IllegalArgumentException("Invalid session ID format");
        }
        Session session = Session.retrieve(sid);
        String ref = session.getClientReferenceId();
        String expected = "user_" + userId;
        if (ref == null || !ref.equals(expected)) {
            logger.warn("Sync from session rejected: client_reference_id does not match user {}", userId);
            throw new IllegalArgumentException("Session does not belong to this user");
        }
        if (!"subscription".equals(session.getMode())) {
            logger.debug("Sync from session: mode is not subscription, skipping");
            return false;
        }
        handleCheckoutSessionCompleted(session);
        return true;
    }

    /**
     * Handle checkout.session.completed: sync subscription from Stripe so the user sees active plan
     * without relying on customer.subscription.created (which may be delayed or not configured).
     * Security: only process subscription-mode sessions; validate subscription ID format before retrieve.
     */
    public void handleCheckoutSessionCompleted(Session session) throws StripeException {
        if (session == null) return;
        Object subRef = session.getSubscription();
        if (subRef == null) return;
        com.stripe.model.Subscription stripeSubscription;
        if (subRef instanceof com.stripe.model.Subscription) {
            stripeSubscription = (com.stripe.model.Subscription) subRef;
        } else {
            String subscriptionId = subRef.toString().trim();
            if (subscriptionId.isEmpty() || !STRIPE_SUBSCRIPTION_ID.matcher(subscriptionId).matches()) {
                logger.warn("Checkout session completed with invalid or missing subscription ID format, skipping");
                return;
            }
            stripeSubscription = com.stripe.model.Subscription.retrieve(subscriptionId);
        }
        handleSubscriptionCreated(stripeSubscription);
        logger.info("Synced subscription from checkout.session.completed for subscription: {}", stripeSubscription.getId());
    }

    /**
     * Handle successful subscription creation
     */
    public void handleSubscriptionCreated(com.stripe.model.Subscription stripeSubscription) {
        String customerId = stripeSubscription.getCustomer();
        if (customerId == null || customerId.isBlank()) {
            logger.warn("Subscription created event with missing customer ID, skipping");
            return;
        }
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
        planFromPriceId(priceId).ifPresent(subscription::setPlan);
        subscription.setCurrentPeriodStart(convertToLocalDateTime(firstItem.getCurrentPeriodStart()));
        subscription.setCurrentPeriodEnd(convertToLocalDateTime(firstItem.getCurrentPeriodEnd()));

        // A new subscription is a clean slate: clear payment-failure state from any previous
        // cycle so a returning customer isn't instantly marked UNPAID on their first hiccup.
        subscription.setPaymentRetryCount(0);
        subscription.setLastPaymentAttempt(null);
        subscription.setGracePeriodEnd(null);
        subscription.setCanceledAt(null);

        subscriptionRepository.save(subscription);
        logger.info("Subscription created for user: {}", LogSanitizer.sanitize(subscription.getUser().getEmail()));
    }

    /**
     * Handle subscription update
     */
    public void handleSubscriptionUpdated(com.stripe.model.Subscription stripeSubscription) {
        if (stripeSubscription == null || stripeSubscription.getId() == null || stripeSubscription.getId().isBlank()) {
            logger.warn("Subscription updated event with missing subscription ID, skipping");
            return;
        }
        Optional<Subscription> subscriptionOpt = subscriptionRepository
            .findByStripeSubscriptionId(stripeSubscription.getId());

        // Webhook ordering: subscription.updated can arrive before subscription.created has
        // stored the Stripe subscription ID locally — fall back to the customer ID like created does.
        if (subscriptionOpt.isEmpty() && stripeSubscription.getCustomer() != null) {
            subscriptionOpt = subscriptionRepository.findByStripeCustomerId(stripeSubscription.getCustomer());
        }

        if (subscriptionOpt.isEmpty()) {
            logger.error("No subscription found for Stripe subscription: {}", stripeSubscription.getId());
            return;
        }

        Subscription subscription = subscriptionOpt.get();
        subscription.setStripeSubscriptionId(stripeSubscription.getId());
        subscription.setStatus(mapStripeStatus(stripeSubscription.getStatus()));
        // Get billing periods and plan from subscription item (Stripe API 2025-03-31+)
        if (stripeSubscription.getItems() != null
                && stripeSubscription.getItems().getData() != null
                && !stripeSubscription.getItems().getData().isEmpty()) {
            com.stripe.model.SubscriptionItem firstItem = stripeSubscription.getItems().getData().get(0);
            subscription.setCurrentPeriodStart(convertToLocalDateTime(firstItem.getCurrentPeriodStart()));
            subscription.setCurrentPeriodEnd(convertToLocalDateTime(firstItem.getCurrentPeriodEnd()));
            if (firstItem.getPrice() != null) {
                String priceId = firstItem.getPrice().getId();
                if (priceId != null && isAllowedPriceId(priceId)) {
                    subscription.setStripePriceId(priceId);
                    planFromPriceId(priceId).ifPresent(subscription::setPlan);
                }
            }
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
        if (stripeSubscription == null || stripeSubscription.getId() == null || stripeSubscription.getId().isBlank()) {
            logger.warn("Subscription deleted event with missing subscription ID, skipping");
            return;
        }
        Optional<Subscription> subscriptionOpt = subscriptionRepository
            .findByStripeSubscriptionId(stripeSubscription.getId());

        if (subscriptionOpt.isEmpty()) {
            logger.error("No subscription found for Stripe subscription: {}", stripeSubscription.getId());
            return;
        }

        Subscription subscription = subscriptionOpt.get();
        subscription.setStatus(Subscription.SubscriptionStatus.CANCELED);
        subscription.setCanceledAt(LocalDateTime.now());
        // Canceled subscriptions fall back to the free tier; keep plan consistent with access level.
        subscription.setPlan(Subscription.SubscriptionPlan.FREE);

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

        // Plan change applies at period end in Stripe; local plan syncs via customer.subscription.updated webhook.
        logger.info("Scheduled downgrade for user ID {} to plan {} at period end", userId, newPlan);
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
        Subscription.SubscriptionPlan planForPrice = planFromPriceId(newPriceId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown price ID for plan change"));
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
     * Handle payment failure with grace period and retry logic.
     * Pessimistic lock + transaction: concurrent webhook deliveries must not interleave
     * the read-modify-write on retry counters.
     */
    @org.springframework.transaction.annotation.Transactional
    public void handlePaymentFailure(String stripeSubscriptionId, String invoiceId) {
        Optional<Subscription> subscriptionOpt = subscriptionRepository
            .findByStripeSubscriptionIdWithLock(stripeSubscriptionId);

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
    @org.springframework.transaction.annotation.Transactional
    public void handlePaymentSuccess(String stripeSubscriptionId) {
        Optional<Subscription> subscriptionOpt = subscriptionRepository
            .findByStripeSubscriptionIdWithLock(stripeSubscriptionId);

        if (subscriptionOpt.isEmpty()) {
            logger.error("No subscription found for Stripe subscription: {}", stripeSubscriptionId);
            return;
        }

        Subscription subscription = subscriptionOpt.get();

        // Reset retry counters and grace period
        subscription.setPaymentRetryCount(0);
        subscription.setLastPaymentAttempt(null);
        subscription.setGracePeriodEnd(null);
        // Don't resurrect a canceled subscription from a late-arriving invoice event.
        if (subscription.getStatus() != Subscription.SubscriptionStatus.CANCELED) {
            subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        }

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
