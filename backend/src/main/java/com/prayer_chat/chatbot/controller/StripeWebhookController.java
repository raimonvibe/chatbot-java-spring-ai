package com.prayer_chat.chatbot.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.prayer_chat.chatbot.service.SecurityAlertService;
import com.prayer_chat.chatbot.service.StripeService;
import com.prayer_chat.chatbot.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for handling Stripe webhook events
 */
@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookController.class);

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Autowired
    private StripeService stripeService;

    @Autowired
    private SecurityAlertService securityAlertService;

    /**
     * Handle Stripe webhook events
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        if (webhookSecret == null || webhookSecret.isBlank()) {
            logger.warn("Stripe webhook secret not configured - rejecting webhook");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Webhook not configured");
        }
        if (sigHeader == null || sigHeader.isBlank()) {
            logger.warn("Stripe webhook called without Stripe-Signature header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing signature");
        }

        Event event;

        try {
            // Verify webhook signature
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            logger.error("Invalid Stripe webhook signature: {}", LogSanitizer.sanitizeException(e));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            logger.error("Error processing Stripe webhook: {}", LogSanitizer.sanitizeException(e));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook error");
        }

        // Handle the event
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = null;

        if (dataObjectDeserializer.getObject().isPresent()) {
            stripeObject = dataObjectDeserializer.getObject().get();
        } else {
            logger.error("Failed to deserialize Stripe event data");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Deserialization error");
        }

        try {
            switch (event.getType()) {
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
                    // TODO: Update when Stripe SDK API for accessing subscription from invoice is clarified
                    // com.stripe.model.Invoice successInvoice = (com.stripe.model.Invoice) stripeObject;
                    logger.info("Invoice payment succeeded - handled via subscription events");
                    break;

                case "invoice.payment_failed":
                    // TODO: Update when Stripe SDK API for accessing subscription from invoice is clarified
                    // com.stripe.model.Invoice failedInvoice = (com.stripe.model.Invoice) stripeObject;
                    logger.warn("Invoice payment failed - handled via subscription events");
                    securityAlertService.alertPaymentFailure("Stripe invoice.payment_failed", null, event.getId());
                    break;

                default:
                    logger.info("Unhandled Stripe event type: {}", event.getType());
            }
        } catch (Exception e) {
            logger.error("Error handling Stripe webhook event {}: {}", event.getType(), LogSanitizer.sanitizeException(e));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing event");
        }

        return ResponseEntity.ok("Webhook received");
    }
}
