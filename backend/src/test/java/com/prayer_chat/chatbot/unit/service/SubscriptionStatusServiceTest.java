package com.prayer_chat.chatbot.unit.service;

import com.prayer_chat.chatbot.config.BillingProperties;
import com.prayer_chat.chatbot.dto.SubscriptionStatusResponse;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import com.prayer_chat.chatbot.service.AccessControlService;
import com.prayer_chat.chatbot.service.RateLimitingService;
import com.prayer_chat.chatbot.service.SubscriptionStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionStatusServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private RateLimitingService rateLimitingService;
    @Mock
    private BillingProperties billingProperties;

    private SubscriptionStatusService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new SubscriptionStatusService(
            subscriptionRepository, accessControlService, rateLimitingService, billingProperties);
        user = new User();
        user.setId(1L);
        when(billingProperties.isEnabled()).thenReturn(true);
        when(rateLimitingService.getWebsiteScanQuotaSnapshot(user))
            .thenReturn(new RateLimitingService.WebsiteScanQuotaSnapshot(5, 1, 3, 0, 4));
    }

    @Test
    void buildForUser_withoutSubscription_returnsFreePreviewWhenEligible() {
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(accessControlService.isPreviewMode(user)).thenReturn(true);

        SubscriptionStatusResponse response = service.buildForUser(user);

        assertThat(response.hasSubscription()).isFalse();
        assertThat(response.plan()).isEqualTo("FREE");
        assertThat(response.canUseChatbot()).isTrue();
        assertThat(response.websiteScansRemaining()).isEqualTo(4);
    }

    @Test
    void buildForUser_withActiveSubscription_includesPlanAndScanQuota() {
        Subscription subscription = new Subscription();
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription.setPlan(Subscription.SubscriptionPlan.BASIC);
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.of(subscription));

        SubscriptionStatusResponse response = service.buildForUser(user, true);

        assertThat(response.hasSubscription()).isTrue();
        assertThat(response.plan()).isEqualTo("BASIC");
        assertThat(response.canUseChatbot()).isTrue();
        assertThat(response.synced()).isTrue();
        assertThat(response.websiteScansMonthlyQuota()).isEqualTo(5);
    }
}
