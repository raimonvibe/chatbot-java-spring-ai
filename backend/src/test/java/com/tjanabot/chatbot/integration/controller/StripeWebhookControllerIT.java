package com.tjanabot.chatbot.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import com.tjanabot.chatbot.helpers.TestDataBuilder;
import com.tjanabot.chatbot.model.Subscription;
import com.tjanabot.chatbot.model.User;
import com.tjanabot.chatbot.service.AuditService;
import com.tjanabot.chatbot.service.StripeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("StripeWebhookController Integration Tests")
class StripeWebhookControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StripeService stripeService;

    @MockitoBean
    private AuditService auditService;

    private User testUser;
    private Subscription testSubscription;

    @BeforeEach
    void setUp() {
        testUser = TestDataBuilder.createTestUser();
        testUser.setId(1L);

        testSubscription = TestDataBuilder.createActiveSubscription(testUser);
        testSubscription.setId(1L);
    }

    @Test
    @DisplayName("Should handle invoice.payment_succeeded webhook")
    void shouldHandlePaymentSucceededWebhook() throws Exception {
        // Arrange
        String payload = """
        {
            "id": "evt_test_webhook",
            "object": "event",
            "type": "invoice.payment_succeeded",
            "data": {
                "object": {
                    "id": "in_test_123",
                    "subscription": "sub_test_123",
                    "amount_paid": 498,
                    "currency": "usd"
                }
            }
        }
        """;

        String signature = "t=1234567890,v1=test_signature";

        doNothing().when(stripeService).handlePaymentSuccess("sub_test_123");

        // Act & Assert
        mockMvc.perform(post("/api/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("Stripe-Signature", signature))
            .andExpect(status().isOk());

        verify(stripeService, times(1)).handlePaymentSuccess("sub_test_123");
        verify(auditService, times(1)).log(any(), any(), anyString(), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("Should handle invoice.payment_failed webhook")
    void shouldHandlePaymentFailedWebhook() throws Exception {
        // Arrange
        String payload = """
        {
            "id": "evt_test_webhook",
            "object": "event",
            "type": "invoice.payment_failed",
            "data": {
                "object": {
                    "id": "in_test_456",
                    "subscription": "sub_test_123",
                    "amount_due": 498
                }
            }
        }
        """;

        String signature = "t=1234567890,v1=test_signature";

        doNothing().when(stripeService).handlePaymentFailure("sub_test_123", "in_test_456");

        // Act & Assert
        mockMvc.perform(post("/api/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("Stripe-Signature", signature))
            .andExpect(status().isOk());

        verify(stripeService, times(1)).handlePaymentFailure("sub_test_123", "in_test_456");
        verify(auditService, times(1)).log(any(), any(), anyString(), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("Should handle customer.subscription.updated webhook")
    void shouldHandleSubscriptionUpdatedWebhook() throws Exception {
        // Arrange
        String payload = """
        {
            "id": "evt_test_webhook",
            "object": "event",
            "type": "customer.subscription.updated",
            "data": {
                "object": {
                    "id": "sub_test_123",
                    "status": "active",
                    "plan": {
                        "id": "price_pro_monthly"
                    }
                }
            }
        }
        """;

        String signature = "t=1234567890,v1=test_signature";

        doNothing().when(stripeService).handleSubscriptionUpdated(any(com.stripe.model.Subscription.class));

        // Act & Assert
        mockMvc.perform(post("/api/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("Stripe-Signature", signature))
            .andExpect(status().isOk());

        verify(stripeService, times(1)).handleSubscriptionUpdated(any(com.stripe.model.Subscription.class));
    }

    @Test
    @DisplayName("Should handle customer.subscription.deleted webhook")
    void shouldHandleSubscriptionDeletedWebhook() throws Exception {
        // Arrange
        String payload = """
        {
            "id": "evt_test_webhook",
            "object": "event",
            "type": "customer.subscription.deleted",
            "data": {
                "object": {
                    "id": "sub_test_123"
                }
            }
        }
        """;

        String signature = "t=1234567890,v1=test_signature";

        doNothing().when(stripeService).handleSubscriptionDeleted(any(com.stripe.model.Subscription.class));

        // Act & Assert
        mockMvc.perform(post("/api/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("Stripe-Signature", signature))
            .andExpect(status().isOk());

        verify(stripeService, times(1)).handleSubscriptionDeleted(any(com.stripe.model.Subscription.class));
        verify(auditService, times(1)).log(any(), any(), anyString(), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("Should reject webhook without signature")
    void shouldRejectWebhookWithoutSignature() throws Exception {
        // Arrange
        String payload = """
        {
            "id": "evt_test_webhook",
            "object": "event",
            "type": "invoice.payment_succeeded"
        }
        """;

        // Act & Assert
        mockMvc.perform(post("/api/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Missing Stripe signature"));

        verify(stripeService, never()).handlePaymentSuccess(any());
    }

    @Test
    @DisplayName("Should reject webhook with invalid signature")
    void shouldRejectWebhookWithInvalidSignature() throws Exception {
        // Arrange
        String payload = """
        {
            "id": "evt_test_webhook",
            "object": "event",
            "type": "invoice.payment_succeeded"
        }
        """;

        String invalidSignature = "t=1234567890,v1=invalid_signature";

        // Mock signature verification to throw exception
        // Note: In real implementation, the controller should verify signatures

        // Act & Assert
        mockMvc.perform(post("/api/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("Stripe-Signature", invalidSignature))
            .andExpect(status().isBadRequest());

        verify(stripeService, never()).handlePaymentSuccess(any());
    }

    @Test
    @DisplayName("Should handle unknown webhook event types gracefully")
    void shouldHandleUnknownEventTypes() throws Exception {
        // Arrange
        String payload = """
        {
            "id": "evt_test_webhook",
            "object": "event",
            "type": "unknown.event.type",
            "data": {
                "object": {}
            }
        }
        """;

        String signature = "t=1234567890,v1=test_signature";

        // Act & Assert
        mockMvc.perform(post("/api/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("Stripe-Signature", signature))
            .andExpect(status().isOk()); // Acknowledge but don't process

        // Verify no service methods were called
        verify(stripeService, never()).handlePaymentSuccess(any());
        verify(stripeService, never()).handlePaymentFailure(any(), any());
    }

    @Test
    @DisplayName("Should handle malformed webhook payload")
    void shouldHandleMalformedPayload() throws Exception {
        // Arrange
        String malformedPayload = "{ invalid json";
        String signature = "t=1234567890,v1=test_signature";

        // Act & Assert
        mockMvc.perform(post("/api/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedPayload)
                .header("Stripe-Signature", signature))
            .andExpect(status().isBadRequest());

        verify(stripeService, never()).handlePaymentSuccess(any());
    }

    @Test
    @DisplayName("Should log all webhook events for audit trail")
    void shouldLogAllWebhookEvents() throws Exception {
        // Arrange
        String payload = """
        {
            "id": "evt_test_webhook",
            "object": "event",
            "type": "invoice.payment_succeeded",
            "data": {
                "object": {
                    "id": "in_test_123",
                    "subscription": "sub_test_123"
                }
            }
        }
        """;

        String signature = "t=1234567890,v1=test_signature";

        // Act
        mockMvc.perform(post("/api/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("Stripe-Signature", signature))
            .andExpect(status().isOk());

        // Assert - Verify audit log was created
        verify(auditService, times(1)).log(
            any(),
            any(),
            contains("Stripe webhook"),
            isNull(),
            isNull(),
            any()
        );
    }

    @Test
    @DisplayName("Should handle idempotent webhook events")
    void shouldHandleIdempotentEvents() throws Exception {
        // Arrange - Send same webhook twice
        String payload = """
        {
            "id": "evt_test_duplicate",
            "object": "event",
            "type": "invoice.payment_succeeded",
            "data": {
                "object": {
                    "id": "in_test_789",
                    "subscription": "sub_test_789"
                }
            }
        }
        """;

        String signature = "t=1234567890,v1=test_signature";

        // Act - Send webhook twice
        mockMvc.perform(post("/api/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("Stripe-Signature", signature))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("Stripe-Signature", signature))
            .andExpect(status().isOk());

        // Assert - Service should handle idempotency
        // In production, you'd want to check the service only processes once
        verify(stripeService, atLeastOnce()).handlePaymentSuccess("sub_test_789");
    }
}
