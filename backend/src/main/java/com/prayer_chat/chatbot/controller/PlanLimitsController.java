package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.config.BillingProperties;
import com.prayer_chat.chatbot.config.PlanLimits;
import com.prayer_chat.chatbot.model.Subscription;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Public API for plan limits (page tiers). Single source of truth is {@link PlanLimits}.
 * Use this so the frontend can display plan limits without duplicating constants.
 */
@RestController
@RequestMapping("/api/plans")
public class PlanLimitsController {

    private final BillingProperties billingProperties;

    public PlanLimitsController(BillingProperties billingProperties) {
        this.billingProperties = billingProperties;
    }

    /**
     * Returns standard plan limits keyed by plan name. No auth required (public pricing info).
     */
    @GetMapping("/limits")
    public ResponseEntity<Map<String, Object>> getPlanLimits() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("description", "One chatbot per subscription. Plan tier = max website pages per scan.");
        body.put("billingEnabled", billingProperties.isEnabled());
        body.put("maxPagesPerScanOffered", PlanLimits.FREE_MAX_PAGES);
        body.put("websiteScanPolicySummary",
            "We scan up to " + PlanLimits.FREE_MAX_PAGES
                + " pages per website. Larger sites cannot be fully indexed in one run; try a smaller URL or subdomain.");
        Map<String, Map<String, Object>> plans = new LinkedHashMap<>();
        Stream.of(Subscription.SubscriptionPlan.values()).forEach(plan -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("maxPagesPerScan", PlanLimits.maxPagesPerScan(plan));
            // When billing is disabled, the FREE card is a "free product" and should reflect the
            // same server-enforced quotas as BillingModeService.
            boolean billingEnabled = billingProperties.isEnabled();
            if (!billingEnabled && plan == Subscription.SubscriptionPlan.FREE) {
                p.put("monthlyScanQuota", 3);
                p.put("messagesPerDay", 30);
            } else {
                p.put("monthlyScanQuota", PlanLimits.monthlyScanQuota(plan));
                p.put("messagesPerDay", PlanLimits.messagesPerDay(plan));
            }
            p.put("maxChatbots", PlanLimits.maxChatbots(plan));
            plans.put(plan.name(), p);
        });
        body.put("plans", plans);
        body.put("standardPageTiers", Map.of(
            "FREE", PlanLimits.FREE_MAX_PAGES,
            "BASIC", PlanLimits.BASIC_MAX_PAGES,
            "PRO", PlanLimits.PRO_MAX_PAGES,
            "ENTERPRISE", PlanLimits.ENTERPRISE_MAX_PAGES
        ));
        return ResponseEntity.ok(body);
    }
}
