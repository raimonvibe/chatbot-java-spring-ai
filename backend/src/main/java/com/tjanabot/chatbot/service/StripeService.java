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

    @Value("${stripe.success-url:http://localhost:3000/dashboard}")
    private String successUrl;

    @Value("${stripe.cancel-url:http://localhost:3000/pricing}")
    private String cancelUrl;

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

        // Create checkout session
        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setCustomer(customerId)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(stripePriceId)
                    .setQuantity(1L)
                    .build()
            )
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
