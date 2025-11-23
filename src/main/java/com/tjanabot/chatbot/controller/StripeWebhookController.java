package com.tjanabot.chatbot.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.tjanabot.chatbot.service.StripeService;
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

    /**
     * Handle Stripe webhook events
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;

        try {
            // Verify webhook signature
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            logger.error("Invalid Stripe webhook signature", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            logger.error("Error processing Stripe webhook", e);
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
                    logger.info("Payment succeeded for invoice");
                    break;

                case "invoice.payment_failed":
                    logger.warn("Payment failed for invoice");
                    break;

                default:
                    logger.info("Unhandled Stripe event type: {}", event.getType());
            }
        } catch (Exception e) {
            logger.error("Error handling Stripe webhook event: {}", event.getType(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing event");
        }

        return ResponseEntity.ok("Webhook received");
    }
}
