package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.config.BillingProperties;
import com.prayer_chat.chatbot.dto.SubscriptionStatusResponse;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Builds subscription status responses for REST endpoints.
 */
@Service
public class SubscriptionStatusService {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionStatusService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final AccessControlService accessControlService;
    private final RateLimitingService rateLimitingService;
    private final BillingProperties billingProperties;

    public SubscriptionStatusService(SubscriptionRepository subscriptionRepository,
                                     AccessControlService accessControlService,
                                     RateLimitingService rateLimitingService,
                                     BillingProperties billingProperties) {
        this.subscriptionRepository = subscriptionRepository;
        this.accessControlService = accessControlService;
        this.rateLimitingService = rateLimitingService;
        this.billingProperties = billingProperties;
    }

    public SubscriptionStatusResponse buildForUser(User user) {
        return buildForUser(user, null);
    }

    public SubscriptionStatusResponse buildForUser(User user, Boolean synced) {
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByUserId(user.getId());
        boolean billingEnabled = billingProperties.isEnabled();

        SubscriptionStatusResponse base;
        if (subscriptionOpt.isPresent()) {
            Subscription subscription = subscriptionOpt.get();
            base = new SubscriptionStatusResponse(
                billingEnabled,
                billingEnabled,
                true,
                subscription.getStatus() != null ? subscription.getStatus().name() : null,
                subscription.getPlan() != null ? subscription.getPlan().name() : null,
                subscription.isActive(),
                subscription.canUseChatbot() || accessControlService.isPreviewMode(user),
                subscription.getCurrentPeriodEnd(),
                subscription.getCanceledAt(),
                null, null, null, null, null,
                synced
            );
        } else {
            boolean previewAccess = accessControlService.isPreviewMode(user);
            base = new SubscriptionStatusResponse(
                billingEnabled,
                billingEnabled,
                false,
                "FREE",
                "FREE",
                false,
                previewAccess,
                null,
                null,
                null, null, null, null, null,
                synced
            );
        }

        return attachScanQuota(user, base);
    }

    private SubscriptionStatusResponse attachScanQuota(User user, SubscriptionStatusResponse base) {
        try {
            RateLimitingService.WebsiteScanQuotaSnapshot scan = rateLimitingService.getWebsiteScanQuotaSnapshot(user);
            return new SubscriptionStatusResponse(
                base.billingEnabled(),
                base.paymentActionsAvailable(),
                base.hasSubscription(),
                base.status(),
                base.plan(),
                base.isActive(),
                base.canUseChatbot(),
                base.currentPeriodEnd(),
                base.canceledAt(),
                scan.monthlyQuota(),
                (int) scan.usedThisMonth(),
                scan.dailyLimit(),
                scan.usedScansInRollingWindow(),
                scan.remainingScansEffective(),
                base.synced()
            );
        } catch (Exception ex) {
            logger.warn("Could not attach website scan quota to subscription status: {}", ex.getMessage());
            return base;
        }
    }
}
