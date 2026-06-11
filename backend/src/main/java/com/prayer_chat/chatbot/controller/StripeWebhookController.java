package com.prayer_chat.chatbot.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.prayer_chat.chatbot.config.BillingProperties;
import com.prayer_chat.chatbot.security.ClientIpResolver;
import com.prayer_chat.chatbot.service.SecurityAlertService;
import com.prayer_chat.chatbot.service.StripeService;
import com.prayer_chat.chatbot.util.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Controller for handling Stripe webhook events.
 * Security: (1) Signature verification (Webhook.constructEvent) prevents forgery and rejects replayed payloads
 * outside Stripe's timestamp tolerance. (2) Event ID deduplication prevents duplicate processing. (3) Optional
 * IP allowlist adds defense in depth. Never log raw payload or webhook secret.
 */
@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookController.class);

    /** Max length for event ID to avoid memory exhaustion in dedup map. Stripe IDs are short (e.g. evt_xxx). */
    private static final int MAX_EVENT_ID_LENGTH = 255;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${stripe.webhook-ip-allowlist:}")
    private String webhookIpAllowlist;

    @Autowired
    private StripeService stripeService;

    @Autowired
    private SecurityAlertService securityAlertService;

    @Autowired
    private BillingProperties billingProperties;

    @Autowired
    private ClientIpResolver clientIpResolver;

    /** Processed Stripe event IDs to prevent duplicate processing (replay / retries). Evicted after 24h. */
    private static final ConcurrentHashMap<String, Long> PROCESSED_EVENT_IDS = new ConcurrentHashMap<>();
    private static final long EVENT_ID_TTL_MS = 24 * 60 * 60 * 1000L;
    private static final int MAX_PROCESSED_EVENT_IDS = 100_000;

    private ConcurrentHashMap<String, Long> processedEventIds() {
        return PROCESSED_EVENT_IDS;
    }

    private void evictOldProcessedEvents() {
        // Time-based eviction on every successful webhook keeps the map bounded under normal load.
        long cutoff = System.currentTimeMillis() - EVENT_ID_TTL_MS;
        PROCESSED_EVENT_IDS.entrySet().removeIf(e -> e.getValue() != null && e.getValue() < cutoff);
        // Hard cap as a safety net (e.g. clock issues): drop everything rather than grow unbounded.
        if (PROCESSED_EVENT_IDS.size() >= MAX_PROCESSED_EVENT_IDS) {
            logger.warn("Processed-event map hit {} entries; clearing", MAX_PROCESSED_EVENT_IDS);
            PROCESSED_EVENT_IDS.clear();
        }
    }

    private Set<String> allowedWebhookIps() {
        if (webhookIpAllowlist == null || webhookIpAllowlist.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(webhookIpAllowlist.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());
    }

    /**
     * Handle Stripe webhook events
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader,
            HttpServletRequest request) {

        if (webhookSecret == null || webhookSecret.isBlank()) {
            logger.warn("Stripe webhook secret not configured - rejecting webhook");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Webhook not configured");
        }
        if (sigHeader == null || sigHeader.isBlank()) {
            logger.warn("Stripe webhook called without Stripe-Signature header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing signature");
        }

        Set<String> allowedIps = allowedWebhookIps();
        if (!allowedIps.isEmpty()) {
            String clientIp = clientIpResolver.resolveClientIp(request);
            if (clientIp == null || !allowedIps.contains(clientIp)) {
                logger.warn("Stripe webhook rejected: IP {} not in allowlist", LogSanitizer.sanitize(clientIp != null ? clientIp : "unknown"));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("IP not allowed");
            }
        }

        Event event;

        try {
            // Verify webhook signature (Stripe SDK also validates timestamp tolerance to prevent replay)
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            logger.error("Invalid Stripe webhook signature: {}", LogSanitizer.sanitizeException(e));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            logger.error("Error processing Stripe webhook: {}", LogSanitizer.sanitizeException(e));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook error");
        }

        if (!billingProperties.isEnabled()) {
            logger.info("Stripe webhook acknowledged but billing disabled (app.billing.enabled=false); event type: {}", event.getType());
            return ResponseEntity.ok("Billing disabled");
        }

        // Idempotency: atomically claim the event ID up-front (prevents two concurrent deliveries
        // both processing). On handler failure the claim is released so Stripe's retry can succeed.
        String eventId = event.getId();
        boolean claimed = false;
        if (eventId != null && !eventId.isBlank()) {
            if (eventId.length() > MAX_EVENT_ID_LENGTH) {
                logger.warn("Stripe webhook event ID too long, rejecting");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid event id");
            }
            Long prior = processedEventIds().putIfAbsent(eventId, System.currentTimeMillis());
            if (prior != null) {
                logger.info("Stripe webhook event already processed or in flight, skipping: {}", eventId);
                return ResponseEntity.ok("Webhook received");
            }
            claimed = true;
        }

        // Handle the event
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = null;

        if (dataObjectDeserializer.getObject().isPresent()) {
            stripeObject = dataObjectDeserializer.getObject().get();
        } else {
            logger.error("Failed to deserialize Stripe event data");
            if (claimed) {
                processedEventIds().remove(eventId);
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Deserialization error");
        }

        try {
            switch (event.getType()) {
                case "checkout.session.completed":
                    Session checkoutSession = (Session) stripeObject;
                    // Only process subscription checkouts; ignore one-time payment sessions
                    if ("subscription".equals(checkoutSession.getMode())) {
                        stripeService.handleCheckoutSessionCompleted(checkoutSession);
                        logger.info("Handled checkout.session.completed event");
                    } else {
                        logger.debug("Ignoring checkout.session.completed for non-subscription mode: {}", checkoutSession.getMode());
                    }
                    break;

                case "customer.subscription.created":
                    com.stripe.model.Subscription subscription = (com.stripe.model.Subscription) stripeObject;
                    stripeService.handleSubscriptionCreated(subscription);
                    logger.info("Handled subscription.created event");
                    break;

                case "customer.subscription.updated":
                    subscription = (com.stripe.model.Subscription) stripeObject;
                    stripeService.handleSubscriptionUpdated(subscription);
                    logger.info("Handled subscription.updated event");
                    break;

                case "customer.subscription.deleted":
                    subscription = (com.stripe.model.Subscription) stripeObject;
                    stripeService.handleSubscriptionDeleted(subscription);
                    logger.info("Handled subscription.deleted event");
                    break;

                case "invoice.payment_succeeded":
                    com.stripe.model.Invoice successInvoice = (com.stripe.model.Invoice) stripeObject;
                    String successSubId = extractSubscriptionId(successInvoice, dataObjectDeserializer.getRawJson());
                    if (successSubId != null && !successSubId.isBlank()) {
                        stripeService.handlePaymentSuccess(successSubId);
                        logger.info("Handled invoice.payment_succeeded for subscription {}", successSubId);
                    } else {
                        logger.info("Invoice payment succeeded without subscription reference");
                    }
                    break;

                case "invoice.payment_failed":
                    com.stripe.model.Invoice failedInvoice = (com.stripe.model.Invoice) stripeObject;
                    String failedSubId = extractSubscriptionId(failedInvoice, dataObjectDeserializer.getRawJson());
                    if (failedSubId != null && !failedSubId.isBlank()) {
                        stripeService.handlePaymentFailure(failedSubId, failedInvoice.getId());
                        logger.warn("Handled invoice.payment_failed for subscription {}", failedSubId);
                    } else {
                        logger.warn("Invoice payment failed without subscription reference");
                    }
                    securityAlertService.alertPaymentFailure(
                        "Stripe invoice.payment_failed (subscription: " + (failedSubId != null ? failedSubId : "unknown") + ")",
                        null, event.getId());
                    break;

                default:
                    logger.info("Unhandled Stripe event type: {}", event.getType());
            }
        } catch (Exception e) {
            logger.error("Error handling Stripe webhook event {}: {}", event.getType(), LogSanitizer.sanitizeException(e));
            // Release the idempotency claim so Stripe's retry is not silently swallowed.
            if (claimed) {
                processedEventIds().remove(eventId);
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing event");
        }

        evictOldProcessedEvents();

        return ResponseEntity.ok("Webhook received");
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper RAW_JSON_MAPPER =
        new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * Stripe API 2025+ moved the invoice's subscription reference to {@code parent.subscription_details.subscription}.
     * Falls back to the legacy top-level {@code subscription} field in the raw event payload so events sent
     * with an older account API version are not silently dropped.
     *
     * @param rawEventJson raw {@code data.object} JSON from the webhook event (may be null)
     */
    private static String extractSubscriptionId(com.stripe.model.Invoice invoice, String rawEventJson) {
        if (invoice != null && invoice.getParent() != null && invoice.getParent().getSubscriptionDetails() != null) {
            String subId = invoice.getParent().getSubscriptionDetails().getSubscription();
            if (subId != null && !subId.isBlank()) {
                return subId;
            }
        }
        if (rawEventJson != null && !rawEventJson.isBlank()) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = RAW_JSON_MAPPER.readTree(rawEventJson);
                com.fasterxml.jackson.databind.JsonNode sub = node.get("subscription");
                if (sub != null && sub.isTextual() && !sub.asText().isBlank()) {
                    return sub.asText();
                }
            } catch (Exception e) {
                logger.debug("Could not read legacy subscription field from invoice payload: {}", e.getMessage());
            }
        }
        return null;
    }
}
