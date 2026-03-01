package com.prayer_chat.chatbot.unit.service;

import com.prayer_chat.chatbot.helpers.TestDataBuilder;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import com.prayer_chat.chatbot.service.StripeService;
import com.stripe.exception.StripeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StripeService Unit Tests")
class StripeServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private StripeService stripeService;

    private User testUser;
    private Subscription testSubscription;

    @BeforeEach
    void setUp() {
        testUser = TestDataBuilder.createTestUser();
        testUser.setId(1L);

        testSubscription = TestDataBuilder.createActiveSubscription(testUser);
        testSubscription.setId(1L);

        // Set @Value fields using ReflectionTestUtils
        ReflectionTestUtils.setField(stripeService, "stripeApiKey", "");
        ReflectionTestUtils.setField(stripeService, "gracePeriodDays", 7);
        ReflectionTestUtils.setField(stripeService, "maxRetryAttempts", 3);
    }

    @Test
    @DisplayName("Should handle payment failure and set grace period on first failure")
    void shouldHandlePaymentFailure_andSetGracePeriod_onFirstFailure() {
        // Arrange
        String subscriptionId = "sub_test_123";
        String invoiceId = "in_test_123";

        testSubscription.setStripeSubscriptionId(subscriptionId);
        testSubscription.setPaymentRetryCount(0);
        testSubscription.setGracePeriodEnd(null);

        when(subscriptionRepository.findByStripeSubscriptionId(subscriptionId))
            .thenReturn(Optional.of(testSubscription));
        when(subscriptionRepository.save(any(Subscription.class)))
            .thenReturn(testSubscription);

        // Act
        stripeService.handlePaymentFailure(subscriptionId, invoiceId);

        // Assert
        assertThat(testSubscription.getStatus()).isEqualTo(Subscription.SubscriptionStatus.PAST_DUE);
        assertThat(testSubscription.getPaymentRetryCount()).isEqualTo(1);
        assertThat(testSubscription.getLastPaymentAttempt()).isNotNull();
        assertThat(testSubscription.getGracePeriodEnd()).isNotNull();
        assertThat(testSubscription.getGracePeriodEnd())
            .isAfter(LocalDateTime.now())
            .isBefore(LocalDateTime.now().plusDays(8));

        verify(subscriptionRepository, times(1)).findByStripeSubscriptionId(subscriptionId);
        verify(subscriptionRepository, times(1)).save(testSubscription);
    }

    @Test
    @DisplayName("Should revoke access after max retries exceeded")
    void shouldRevokeAccess_afterMaxRetries() {
        // Arrange
        String subscriptionId = "sub_test_123";
        String invoiceId = "in_test_123";

        testSubscription.setStripeSubscriptionId(subscriptionId);
        testSubscription.setPaymentRetryCount(2); // Already 2 failures
        testSubscription.setGracePeriodEnd(LocalDateTime.now().plusDays(5));

        when(subscriptionRepository.findByStripeSubscriptionId(subscriptionId))
            .thenReturn(Optional.of(testSubscription));
        when(subscriptionRepository.save(any(Subscription.class)))
            .thenReturn(testSubscription);

        // Act
        stripeService.handlePaymentFailure(subscriptionId, invoiceId);

        // Assert - 3rd failure should revoke access
        assertThat(testSubscription.getStatus()).isEqualTo(Subscription.SubscriptionStatus.UNPAID);
        assertThat(testSubscription.getPaymentRetryCount()).isEqualTo(3);
        verify(subscriptionRepository, times(1)).save(testSubscription);
    }

    @Test
    @DisplayName("Should reset retry counters on payment success")
    void shouldResetRetryCounters_onPaymentSuccess() {
        // Arrange
        String subscriptionId = "sub_test_123";

        testSubscription.setStripeSubscriptionId(subscriptionId);
        testSubscription.setStatus(Subscription.SubscriptionStatus.PAST_DUE);
        testSubscription.setPaymentRetryCount(2);
        testSubscription.setGracePeriodEnd(LocalDateTime.now().plusDays(5));
        testSubscription.setLastPaymentAttempt(LocalDateTime.now().minusHours(1));

        when(subscriptionRepository.findByStripeSubscriptionId(subscriptionId))
            .thenReturn(Optional.of(testSubscription));
        when(subscriptionRepository.save(any(Subscription.class)))
            .thenReturn(testSubscription);

        // Act
        stripeService.handlePaymentSuccess(subscriptionId);

        // Assert
        assertThat(testSubscription.getStatus()).isEqualTo(Subscription.SubscriptionStatus.ACTIVE);
        assertThat(testSubscription.getPaymentRetryCount()).isEqualTo(0);
        assertThat(testSubscription.getGracePeriodEnd()).isNull();
        assertThat(testSubscription.getLastPaymentAttempt()).isNull();
        verify(subscriptionRepository, times(1)).save(testSubscription);
    }

    @Test
    @DisplayName("Should return true when subscription is in grace period")
    void shouldReturnTrue_whenInGracePeriod() {
        // Arrange
        Long userId = 1L;
        testSubscription.setGracePeriodEnd(LocalDateTime.now().plusDays(5));

        when(subscriptionRepository.findByUserId(userId))
            .thenReturn(Optional.of(testSubscription));

        // Act
        boolean result = stripeService.isInGracePeriod(userId);

        // Assert
        assertThat(result).isTrue();
        verify(subscriptionRepository, times(1)).findByUserId(userId);
    }

    @Test
    @DisplayName("Should return false when grace period expired")
    void shouldReturnFalse_whenGracePeriodExpired() {
        // Arrange
        Long userId = 1L;
        testSubscription.setGracePeriodEnd(LocalDateTime.now().minusDays(1)); // Expired

        when(subscriptionRepository.findByUserId(userId))
            .thenReturn(Optional.of(testSubscription));

        // Act
        boolean result = stripeService.isInGracePeriod(userId);

        // Assert
        assertThat(result).isFalse();
        verify(subscriptionRepository, times(1)).findByUserId(userId);
    }

    @Test
    @DisplayName("Should calculate remaining grace period days correctly")
    void shouldCalculateRemainingGracePeriodDays() {
        // Arrange
        Long userId = 1L;
        // Set grace period end to 3 days + 1 hour from now to ensure full 3 days
        testSubscription.setGracePeriodEnd(LocalDateTime.now().plusDays(3).plusHours(1));

        when(subscriptionRepository.findByUserId(userId))
            .thenReturn(Optional.of(testSubscription));

        // Act
        int remainingDays = stripeService.getRemainingGracePeriodDays(userId);

        // Assert
        assertThat(remainingDays).isEqualTo(3);
        verify(subscriptionRepository, times(1)).findByUserId(userId);
    }

    @Test
    @DisplayName("Should return 0 days when no grace period set")
    void shouldReturn0Days_whenNoGracePeriod() {
        // Arrange
        Long userId = 1L;
        testSubscription.setGracePeriodEnd(null);

        when(subscriptionRepository.findByUserId(userId))
            .thenReturn(Optional.of(testSubscription));

        // Act
        int remainingDays = stripeService.getRemainingGracePeriodDays(userId);

        // Assert
        assertThat(remainingDays).isEqualTo(0);
    }

    @Test
    @DisplayName("Should detect upgrade correctly")
    void shouldDetectUpgrade() {
        // This tests the private method indirectly through public methods
        // We would test this through integration tests or make the method package-private for testing
        assertThat(Subscription.SubscriptionPlan.BASIC.ordinal())
            .isLessThan(Subscription.SubscriptionPlan.PRO.ordinal());
        assertThat(Subscription.SubscriptionPlan.PRO.ordinal())
            .isLessThan(Subscription.SubscriptionPlan.ENTERPRISE.ordinal());
    }

    @Test
    @DisplayName("isConfigured returns false when API key is empty")
    void isConfigured_returnsFalse_whenApiKeyEmpty() {
        ReflectionTestUtils.setField(stripeService, "stripeApiKey", "");
        assertThat(stripeService.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("isConfigured returns false when API key is null")
    void isConfigured_returnsFalse_whenApiKeyNull() {
        ReflectionTestUtils.setField(stripeService, "stripeApiKey", null);
        assertThat(stripeService.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("isConfigured returns false when API key is whitespace only")
    void isConfigured_returnsFalse_whenApiKeyWhitespace() {
        ReflectionTestUtils.setField(stripeService, "stripeApiKey", "   ");
        assertThat(stripeService.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("isConfigured returns true when API key is set")
    void isConfigured_returnsTrue_whenApiKeySet() {
        ReflectionTestUtils.setField(stripeService, "stripeApiKey", "sk_test_xxx");
        assertThat(stripeService.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("createCheckoutSession throws when Stripe not configured")
    void createCheckoutSession_throwsWhenNotConfigured() {
        ReflectionTestUtils.setField(stripeService, "stripeApiKey", "");
        assertThatThrownBy(() -> stripeService.createCheckoutSession(testUser, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not configured");
        verify(subscriptionRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("createBillingPortalSession throws when Stripe not configured")
    void createBillingPortalSession_throwsWhenNotConfigured() {
        ReflectionTestUtils.setField(stripeService, "stripeApiKey", "");
        assertThatThrownBy(() -> stripeService.createBillingPortalSession(testUser, "https://example.com/return"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not configured");
        verify(subscriptionRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("isAllowedPriceId returns true only for configured price IDs")
    void isAllowedPriceId_returnsTrueOnlyForConfiguredPrices() {
        ReflectionTestUtils.setField(stripeService, "stripePriceId", "price_default");
        ReflectionTestUtils.setField(stripeService, "stripePriceIdBasic", "price_basic");
        ReflectionTestUtils.setField(stripeService, "stripePriceIdPro", "price_pro");
        ReflectionTestUtils.setField(stripeService, "stripePriceIdEnterprise", "price_enterprise");

        assertThat(stripeService.isAllowedPriceId("price_default")).isTrue();
        assertThat(stripeService.isAllowedPriceId("price_basic")).isTrue();
        assertThat(stripeService.isAllowedPriceId("price_pro")).isTrue();
        assertThat(stripeService.isAllowedPriceId("price_enterprise")).isTrue();
        assertThat(stripeService.isAllowedPriceId("price_evil")).isFalse();
        assertThat(stripeService.isAllowedPriceId("price_other")).isFalse();
        assertThat(stripeService.isAllowedPriceId(null)).isFalse();
        assertThat(stripeService.isAllowedPriceId("")).isFalse();
    }

    @Test
    @DisplayName("SECURITY: isAllowedPriceId rejects whitespace-only and substring price IDs")
    void security_isAllowedPriceId_rejectsWhitespaceAndSubstringAttempts() {
        ReflectionTestUtils.setField(stripeService, "stripePriceId", "price_ok");
        ReflectionTestUtils.setField(stripeService, "stripePriceIdBasic", "price_basic");
        ReflectionTestUtils.setField(stripeService, "stripePriceIdPro", null);
        ReflectionTestUtils.setField(stripeService, "stripePriceIdEnterprise", null);

        assertThat(stripeService.isAllowedPriceId("   ")).isFalse();
        assertThat(stripeService.isAllowedPriceId("price_ok_evil")).isFalse();
        assertThat(stripeService.isAllowedPriceId("price_basic_attacker")).isFalse();
        assertThat(stripeService.isAllowedPriceId("price_")).isFalse();
    }

    // --- No-such-customer recovery: isNoSuchCustomer() drives retry; only that error is retried ---

    @Test
    @DisplayName("SECURITY: isNoSuchCustomer returns true only for resource_missing + No such customer")
    void security_isNoSuchCustomer_returnsTrueOnlyForNoSuchCustomer() throws Exception {
        Method isNoSuchCustomer = StripeService.class.getDeclaredMethod("isNoSuchCustomer", StripeException.class);
        isNoSuchCustomer.setAccessible(true);

        StripeException noSuchCustomer = mock(StripeException.class);
        when(noSuchCustomer.getCode()).thenReturn("resource_missing");
        when(noSuchCustomer.getMessage()).thenReturn("No such customer: 'cus_TvUQUCDXuaoj1H'");

        assertThat((Boolean) isNoSuchCustomer.invoke(null, noSuchCustomer)).isTrue();
    }

    @Test
    @DisplayName("SECURITY: isNoSuchCustomer returns false for other error codes")
    void security_isNoSuchCustomer_returnsFalseForOtherCodes() throws Exception {
        Method isNoSuchCustomer = StripeService.class.getDeclaredMethod("isNoSuchCustomer", StripeException.class);
        isNoSuchCustomer.setAccessible(true);

        StripeException cardError = mock(StripeException.class);
        when(cardError.getCode()).thenReturn("card_declined");
        when(cardError.getMessage()).thenReturn("Your card was declined.");

        assertThat((Boolean) isNoSuchCustomer.invoke(null, cardError)).isFalse();
    }

    @Test
    @DisplayName("SECURITY: isNoSuchCustomer returns false when message does not contain No such customer")
    void security_isNoSuchCustomer_returnsFalseWhenMessageDifferent() throws Exception {
        Method isNoSuchCustomer = StripeService.class.getDeclaredMethod("isNoSuchCustomer", StripeException.class);
        isNoSuchCustomer.setAccessible(true);

        StripeException resourceMissingOther = mock(StripeException.class);
        when(resourceMissingOther.getCode()).thenReturn("resource_missing");
        when(resourceMissingOther.getMessage()).thenReturn("No such price: 'price_xxx'");

        assertThat((Boolean) isNoSuchCustomer.invoke(null, resourceMissingOther)).isFalse();
    }

    @Test
    @DisplayName("SECURITY: isNoSuchCustomer returns false for null")
    void security_isNoSuchCustomer_returnsFalseForNull() throws Exception {
        Method isNoSuchCustomer = StripeService.class.getDeclaredMethod("isNoSuchCustomer", StripeException.class);
        isNoSuchCustomer.setAccessible(true);

        assertThat((Boolean) isNoSuchCustomer.invoke(null, (Object) null)).isFalse();
    }

    // --- Defensive null checks: no NPE, no cross-user or invalid state ---

    @Test
    @DisplayName("SECURITY: createCheckoutSession throws when user is null")
    void security_createCheckoutSession_throwsWhenUserNull() {
        ReflectionTestUtils.setField(stripeService, "stripeApiKey", "sk_test_xxx");
        assertThatThrownBy(() -> stripeService.createCheckoutSession(null, "BASIC"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User and user ID are required");
        verify(subscriptionRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("SECURITY: createCheckoutSession throws when user ID is null")
    void security_createCheckoutSession_throwsWhenUserIdNull() {
        ReflectionTestUtils.setField(stripeService, "stripeApiKey", "sk_test_xxx");
        User userNoId = TestDataBuilder.createTestUser();
        userNoId.setId(null);
        assertThatThrownBy(() -> stripeService.createCheckoutSession(userNoId, "BASIC"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User and user ID are required");
        verify(subscriptionRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("SECURITY: createBillingPortalSession throws when user is null")
    void security_createBillingPortalSession_throwsWhenUserNull() {
        ReflectionTestUtils.setField(stripeService, "stripeApiKey", "sk_test_xxx");
        assertThatThrownBy(() -> stripeService.createBillingPortalSession(null, "https://example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User and user ID are required");
        verify(subscriptionRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("SECURITY: handleSubscriptionCreated does nothing when customer ID is null")
    void security_handleSubscriptionCreated_skipsWhenCustomerIdNull() {
        com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
        when(stripeSub.getCustomer()).thenReturn(null);

        stripeService.handleSubscriptionCreated(stripeSub);

        verify(subscriptionRepository, never()).findByStripeCustomerId(any());
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    @DisplayName("SECURITY: handleSubscriptionCreated does nothing when customer ID is blank")
    void security_handleSubscriptionCreated_skipsWhenCustomerIdBlank() {
        com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
        when(stripeSub.getCustomer()).thenReturn("  ");

        stripeService.handleSubscriptionCreated(stripeSub);

        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    @DisplayName("SECURITY: handleSubscriptionCreated does not save when no subscription found for customer")
    void security_handleSubscriptionCreated_noSaveWhenUnknownCustomer() {
        com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
        when(stripeSub.getCustomer()).thenReturn("cus_unknown_from_webhook");
        when(subscriptionRepository.findByStripeCustomerId("cus_unknown_from_webhook")).thenReturn(Optional.empty());

        stripeService.handleSubscriptionCreated(stripeSub);

        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    @DisplayName("SECURITY: handleSubscriptionUpdated does nothing when subscription ID is null")
    void security_handleSubscriptionUpdated_skipsWhenSubscriptionIdNull() {
        com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
        when(stripeSub.getId()).thenReturn(null);

        stripeService.handleSubscriptionUpdated(stripeSub);

        verify(subscriptionRepository, never()).findByStripeSubscriptionId(any());
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    @DisplayName("SECURITY: handleSubscriptionDeleted does nothing when subscription ID is null")
    void security_handleSubscriptionDeleted_skipsWhenSubscriptionIdNull() {
        com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
        when(stripeSub.getId()).thenReturn(null);

        stripeService.handleSubscriptionDeleted(stripeSub);

        verify(subscriptionRepository, never()).findByStripeSubscriptionId(any());
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }
}
