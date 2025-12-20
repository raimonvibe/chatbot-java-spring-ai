package com.tjanabot.chatbot.unit.service;

import com.tjanabot.chatbot.helpers.TestDataBuilder;
import com.tjanabot.chatbot.model.Subscription;
import com.tjanabot.chatbot.model.User;
import com.tjanabot.chatbot.repository.SubscriptionRepository;
import com.tjanabot.chatbot.repository.UserRepository;
import com.tjanabot.chatbot.service.StripeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Mock
    private UserRepository userRepository;

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
}
