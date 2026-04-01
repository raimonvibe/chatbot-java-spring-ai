package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.config.PlanLimits;
import com.prayer_chat.chatbot.model.Subscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PlanLimitsController.
 * Security: endpoint must only expose public plan limits (no user data, no secrets).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlanLimitsController Tests")
class PlanLimitsControllerTest {

    @InjectMocks
    private PlanLimitsController planLimitsController;

    @Test
    @DisplayName("GET /limits returns 200 and plan limits structure")
    void getPlanLimitsReturnsOkAndStructure() {
        ResponseEntity<Map<String, Object>> response = planLimitsController.getPlanLimits();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        Map<String, Object> body = response.getBody();
        assertTrue(body.containsKey("description"));
        assertTrue(body.containsKey("plans"));
        assertTrue(body.containsKey("standardPageTiers"));

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> plans = (Map<String, Map<String, Object>>) body.get("plans");
        assertNotNull(plans);
        assertEquals(4, plans.size());
        assertTrue(plans.containsKey("FREE"));
        assertTrue(plans.containsKey("BASIC"));
        assertTrue(plans.containsKey("PRO"));
        assertTrue(plans.containsKey("ENTERPRISE"));
    }

    @Test
    @DisplayName("Returned plan values match PlanLimits constants")
    void returnedValuesMatchPlanLimits() {
        ResponseEntity<Map<String, Object>> response = planLimitsController.getPlanLimits();
        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> plans = (Map<String, Map<String, Object>>) body.get("plans");
        assertNotNull(plans);

        assertEquals(PlanLimits.maxPagesPerScan(Subscription.SubscriptionPlan.FREE),
            plans.get("FREE").get("maxPagesPerScan"));
        assertEquals(PlanLimits.maxPagesPerScan(Subscription.SubscriptionPlan.BASIC),
            plans.get("BASIC").get("maxPagesPerScan"));
        assertEquals(PlanLimits.maxPagesPerScan(Subscription.SubscriptionPlan.PRO),
            plans.get("PRO").get("maxPagesPerScan"));
        assertEquals(PlanLimits.maxPagesPerScan(Subscription.SubscriptionPlan.ENTERPRISE),
            plans.get("ENTERPRISE").get("maxPagesPerScan"));

        assertEquals(PlanLimits.maxChatbots(Subscription.SubscriptionPlan.FREE), plans.get("FREE").get("maxChatbots"));
        assertEquals(PlanLimits.monthlyScanQuota(Subscription.SubscriptionPlan.BASIC),
            plans.get("BASIC").get("monthlyScanQuota"));
        assertEquals(PlanLimits.messagesPerDay(Subscription.SubscriptionPlan.PRO),
            plans.get("PRO").get("messagesPerDay"));
    }

    @Test
    @DisplayName("standardPageTiers matches PlanLimits constants")
    void standardPageTiersMatchesConstants() {
        ResponseEntity<Map<String, Object>> response = planLimitsController.getPlanLimits();
        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        @SuppressWarnings("unchecked")
        Map<String, Integer> tiers = (Map<String, Integer>) body.get("standardPageTiers");
        assertNotNull(tiers);
        assertEquals(PlanLimits.FREE_MAX_PAGES, tiers.get("FREE"));
        assertEquals(PlanLimits.BASIC_MAX_PAGES, tiers.get("BASIC"));
        assertEquals(PlanLimits.PRO_MAX_PAGES, tiers.get("PRO"));
        assertEquals(PlanLimits.ENTERPRISE_MAX_PAGES, tiers.get("ENTERPRISE"));
    }

    @Test
    @DisplayName("Response must not contain user data or sensitive keys")
    void responseMustNotContainSensitiveData() {
        ResponseEntity<Map<String, Object>> response = planLimitsController.getPlanLimits();
        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        assertFalse(body.containsKey("user"));
        assertFalse(body.containsKey("userId"));
        assertFalse(body.containsKey("email"));
        assertFalse(body.containsKey("token"));
        assertFalse(body.containsKey("secret"));
        assertFalse(body.containsKey("apiKey"));
    }
}
