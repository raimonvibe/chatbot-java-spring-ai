package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.config.PlanLimits;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import com.prayer_chat.chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CostTrackingService Tests")
class CostTrackingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private BillingModeService billingModeService;

    @InjectMocks
    private CostTrackingService costTrackingService;

    private User previewUser;
    private User paidUser;
    private Subscription paidSubscription;

    @BeforeEach
    void setUp() {
        lenient().when(billingModeService.isBillingEnabled()).thenReturn(true);
        lenient().when(billingModeService.effectiveMonthlyCostCapUsd(any()))
            .thenAnswer(inv -> PlanLimits.monthlyCostCapUsd(inv.getArgument(0)));

        previewUser = new User();
        previewUser.setId(1L);
        previewUser.setEmail("preview@example.com");
        previewUser.setCurrentMonthCost(BigDecimal.ZERO);
        previewUser.setMonthlyCostLimit(new BigDecimal("5.00"));
        previewUser.setCostResetDate(LocalDateTime.now());

        paidUser = new User();
        paidUser.setId(2L);
        paidUser.setEmail("paid@example.com");
        paidUser.setCurrentMonthCost(BigDecimal.ZERO);
        paidUser.setMonthlyCostLimit(new BigDecimal("999999.99"));

        paidSubscription = new Subscription();
        paidSubscription.setUser(paidUser);
        paidSubscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        paidSubscription.setPlan(Subscription.SubscriptionPlan.BASIC);
    }

    @Test
    @DisplayName("Should detect preview mode when user has no subscription")
    void shouldDetectPreviewModeWhenNoSubscription() {
        // Arrange
        when(subscriptionRepository.findByUserId(previewUser.getId())).thenReturn(Optional.empty());

        // Act
        boolean isPreview = costTrackingService.isPreviewMode(previewUser);

        // Assert
        assertThat(isPreview).isTrue();
    }

    @Test
    @DisplayName("Should detect preview mode when user has FREE subscription")
    void shouldDetectPreviewModeWhenFreeSubscription() {
        // Arrange
        Subscription freeSubscription = new Subscription();
        freeSubscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        freeSubscription.setPlan(Subscription.SubscriptionPlan.FREE);
        when(subscriptionRepository.findByUserId(previewUser.getId())).thenReturn(Optional.of(freeSubscription));

        // Act
        boolean isPreview = costTrackingService.isPreviewMode(previewUser);

        // Assert
        assertThat(isPreview).isTrue();
    }

    @Test
    @DisplayName("Should not detect preview mode when user has paid subscription")
    void shouldNotDetectPreviewModeWhenPaidSubscription() {
        // Arrange
        when(subscriptionRepository.findByUserId(paidUser.getId())).thenReturn(Optional.of(paidSubscription));

        // Act
        boolean isPreview = costTrackingService.isPreviewMode(paidUser);

        // Assert
        assertThat(isPreview).isFalse();
    }

    @Test
    @DisplayName("Should calculate website scan cost correctly")
    void shouldCalculateWebsiteScanCost() {
        // Act
        BigDecimal cost = costTrackingService.calculateWebsiteScanCost(10, 20000);

        // Assert
        assertThat(cost).isNotNull();
        assertThat(cost).isGreaterThan(BigDecimal.ZERO);
        // 10 pages * 0.0001 = 0.001, 20000 tokens / 1M * 0.10 = 0.002, total = 0.003
        assertThat(cost).isLessThan(new BigDecimal("0.01"));
    }

    @Test
    @DisplayName("Should allow cost within limit for preview mode")
    void shouldAllowCostWithinLimit() {
        // Arrange
        previewUser.setCurrentMonthCost(new BigDecimal("4.00"));
        previewUser.setCostResetDate(LocalDateTime.now()); // Not expired
        BigDecimal estimatedCost = new BigDecimal("0.50");
        when(subscriptionRepository.findByUserId(previewUser.getId())).thenReturn(Optional.empty());
        when(userRepository.findByIdWithLock(previewUser.getId())).thenReturn(Optional.of(previewUser));

        // Act & Assert - Should not throw
        costTrackingService.checkCostLimit(previewUser, estimatedCost);

        // Verify - resetMonthlyCostIfNeeded won't be called since date is not expired
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should reject cost exceeding limit for preview mode")
    void shouldRejectCostExceedingLimit() {
        // Arrange
        previewUser.setCurrentMonthCost(new BigDecimal("4.90"));
        BigDecimal estimatedCost = new BigDecimal("0.20"); // Would exceed $5.00 limit
        when(subscriptionRepository.findByUserId(previewUser.getId())).thenReturn(Optional.empty());
        when(userRepository.findByIdWithLock(previewUser.getId())).thenReturn(Optional.of(previewUser));

        // Act & Assert
        assertThatThrownBy(() -> costTrackingService.checkCostLimit(previewUser, estimatedCost))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Monthly cost limit reached");
    }

    @Test
    @DisplayName("Should enforce plan cost limit for paid users when over cap (BASIC = $15)")
    void shouldNotEnforceCostLimitForPaidUsers() {
        paidUser.setCurrentMonthCost(BigDecimal.ZERO);
        paidUser.setCostResetDate(LocalDateTime.now());
        BigDecimal estimatedCost = new BigDecimal("50.00");
        when(subscriptionRepository.findByUserId(paidUser.getId())).thenReturn(Optional.of(paidSubscription));
        when(userRepository.findByIdWithLock(paidUser.getId())).thenReturn(Optional.of(paidUser));

        assertThatThrownBy(() -> costTrackingService.checkCostLimit(paidUser, estimatedCost))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Monthly cost limit reached");
    }

    @Test
    @DisplayName("Should track website scan cost for preview mode users")
    void shouldTrackWebsiteScanCostForPreviewMode() {
        // Arrange
        previewUser.setCurrentMonthCost(BigDecimal.ZERO);
        when(subscriptionRepository.findByUserId(previewUser.getId())).thenReturn(Optional.empty());
        when(userRepository.findByIdWithLock(previewUser.getId())).thenReturn(Optional.of(previewUser));
        when(userRepository.save(any(User.class))).thenReturn(previewUser);

        // Act
        costTrackingService.trackWebsiteScanCost(previewUser, 10, 20000);

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getCurrentMonthCost()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should track website scan cost for paid users within plan cap")
    void shouldTrackWebsiteScanCostForPaidUsersWithinCap() {
        paidUser.setCurrentMonthCost(BigDecimal.ZERO);
        paidUser.setCostResetDate(LocalDateTime.now());
        when(subscriptionRepository.findByUserId(paidUser.getId())).thenReturn(Optional.of(paidSubscription));
        when(userRepository.findByIdWithLock(paidUser.getId())).thenReturn(Optional.of(paidUser));
        when(userRepository.save(any(User.class))).thenReturn(paidUser);

        costTrackingService.trackWebsiteScanCost(paidUser, 10, 20000);

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should reset monthly cost when new month starts")
    void shouldResetMonthlyCostWhenNewMonth() {
        // Arrange
        previewUser.setCostResetDate(LocalDateTime.now().minusMonths(2));
        previewUser.setCurrentMonthCost(new BigDecimal("10.00"));
        when(userRepository.save(any(User.class))).thenReturn(previewUser);

        // Act
        costTrackingService.resetMonthlyCostIfNeeded(previewUser);

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getCurrentMonthCost()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should get current month cost")
    void shouldGetCurrentMonthCost() {
        // Arrange
        previewUser.setCurrentMonthCost(new BigDecimal("2.50"));
        previewUser.setCostResetDate(LocalDateTime.now()); // Not expired, so reset won't be called

        // Act
        BigDecimal currentCost = costTrackingService.getCurrentMonthCost(previewUser);

        // Assert
        assertThat(currentCost).isEqualTo(new BigDecimal("2.50"));
    }

    @Test
    @DisplayName("Should get monthly cost limit for preview mode")
    void shouldGetMonthlyCostLimitForPreviewMode() {
        // Arrange
        when(subscriptionRepository.findByUserId(previewUser.getId())).thenReturn(Optional.empty());

        // Act
        BigDecimal limit = costTrackingService.getMonthlyCostLimit(previewUser);

        // Assert
        assertThat(limit).isEqualTo(new BigDecimal("5.00"));
    }

    @Test
    @DisplayName("Should get plan-based cost limit for paid users (BASIC = $15)")
    void shouldGetPlanCostLimitForPaidUsers() {
        when(subscriptionRepository.findByUserId(paidUser.getId())).thenReturn(Optional.of(paidSubscription));
        BigDecimal limit = costTrackingService.getMonthlyCostLimit(paidUser);
        assertThat(limit).isEqualByComparingTo(new BigDecimal("15.00"));
    }
}

