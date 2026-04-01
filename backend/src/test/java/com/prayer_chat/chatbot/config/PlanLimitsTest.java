package com.prayer_chat.chatbot.config;

import com.prayer_chat.chatbot.model.Subscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PlanLimits Tests")
class PlanLimitsTest {

    @Nested
    @DisplayName("maxPagesPerScan")
    class MaxPagesPerScan {
        @Test
        void shouldReturnFreeLimitForNullPlan() {
            assertEquals(PlanLimits.FREE_MAX_PAGES, PlanLimits.maxPagesPerScan(null));
        }

        @Test
        void shouldReturnCorrectPageTiersForEachPlan() {
            assertEquals(500, PlanLimits.maxPagesPerScan(Subscription.SubscriptionPlan.FREE));
            assertEquals(2_000, PlanLimits.maxPagesPerScan(Subscription.SubscriptionPlan.BASIC));
            assertEquals(2_000, PlanLimits.maxPagesPerScan(Subscription.SubscriptionPlan.PRO));
            assertEquals(10_000, PlanLimits.maxPagesPerScan(Subscription.SubscriptionPlan.ENTERPRISE));
        }

        @Test
        void shouldMatchPublicConstants() {
            assertEquals(PlanLimits.FREE_MAX_PAGES, PlanLimits.maxPagesPerScan(Subscription.SubscriptionPlan.FREE));
            assertEquals(PlanLimits.BASIC_MAX_PAGES, PlanLimits.maxPagesPerScan(Subscription.SubscriptionPlan.BASIC));
            assertEquals(PlanLimits.PRO_MAX_PAGES, PlanLimits.maxPagesPerScan(Subscription.SubscriptionPlan.PRO));
            assertEquals(PlanLimits.ENTERPRISE_MAX_PAGES, PlanLimits.maxPagesPerScan(Subscription.SubscriptionPlan.ENTERPRISE));
        }
    }

    @Nested
    @DisplayName("minimumPlanForPages")
    class MinimumPlanForPages {
        @Test
        void shouldReturnFreeForZeroOrNegativePages() {
            assertEquals(Subscription.SubscriptionPlan.FREE, PlanLimits.minimumPlanForPages(0));
            assertEquals(Subscription.SubscriptionPlan.FREE, PlanLimits.minimumPlanForPages(-1));
            assertEquals(Subscription.SubscriptionPlan.FREE, PlanLimits.minimumPlanForPages(-100));
        }

        @ParameterizedTest
        @CsvSource({ "1", "250", "500" })
        void shouldReturnFreeUpTo500Pages(int pages) {
            assertEquals(Subscription.SubscriptionPlan.FREE, PlanLimits.minimumPlanForPages(pages));
        }

        @ParameterizedTest
        @CsvSource({ "501", "1000", "2000" })
        void shouldReturnBasicFor501To2000Pages(int pages) {
            assertEquals(Subscription.SubscriptionPlan.BASIC, PlanLimits.minimumPlanForPages(pages));
        }

        @ParameterizedTest
        @CsvSource({ "2001", "5000", "10000", "100000" })
        void shouldReturnEnterpriseAbove2000Pages(int pages) {
            assertEquals(Subscription.SubscriptionPlan.ENTERPRISE, PlanLimits.minimumPlanForPages(pages));
        }
    }

    @Nested
    @DisplayName("maxChatbots")
    class MaxChatbots {
        @Test
        void shouldAlwaysReturnOneForAllPlans() {
            assertEquals(1, PlanLimits.maxChatbots(null));
            for (Subscription.SubscriptionPlan plan : Subscription.SubscriptionPlan.values()) {
                assertEquals(1, PlanLimits.maxChatbots(plan), "Plan " + plan + " should allow 1 chatbot");
            }
        }
    }

    @Nested
    @DisplayName("monthlyCostCapUsd")
    class MonthlyCostCapUsd {
        @Test
        void shouldReturnSafeDefaultForNullPlan() {
            BigDecimal cap = PlanLimits.monthlyCostCapUsd(null);
            assertNotNull(cap);
            assertTrue(cap.compareTo(BigDecimal.ZERO) > 0);
        }

        @Test
        void shouldReturnPositiveCapForEachPlan() {
            for (Subscription.SubscriptionPlan plan : Subscription.SubscriptionPlan.values()) {
                BigDecimal cap = PlanLimits.monthlyCostCapUsd(plan);
                assertNotNull(cap);
                assertTrue(cap.compareTo(BigDecimal.ZERO) > 0, "Plan " + plan + " should have positive cost cap");
            }
        }
    }

    @Nested
    @DisplayName("monthlyScanQuota and messagesPerDay")
    class OtherLimits {
        @Test
        void shouldReturnSensibleDefaultsForNullPlan() {
            assertTrue(PlanLimits.monthlyScanQuota(null) >= 1);
            assertTrue(PlanLimits.messagesPerDay(null) >= 1);
            assertTrue(PlanLimits.dailyScanLimit(null) >= 1);
        }

        @Test
        void shouldIncreaseOrStaySameFromFreeToEnterprise() {
            int freeScans = PlanLimits.monthlyScanQuota(Subscription.SubscriptionPlan.FREE);
            int basicScans = PlanLimits.monthlyScanQuota(Subscription.SubscriptionPlan.BASIC);
            int proScans = PlanLimits.monthlyScanQuota(Subscription.SubscriptionPlan.PRO);
            int entScans = PlanLimits.monthlyScanQuota(Subscription.SubscriptionPlan.ENTERPRISE);
            assertTrue(basicScans >= freeScans && proScans >= basicScans && entScans >= proScans);
        }
    }
}
