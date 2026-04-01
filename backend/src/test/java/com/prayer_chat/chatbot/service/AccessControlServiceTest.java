package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccessControlService Tests")
class AccessControlServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private CostTrackingService costTrackingService;

    @InjectMocks
    private AccessControlService accessControlService;

    private User testUser;
    private Subscription activePaidSubscription;
    private Subscription activeFreeSubscription;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");

        activePaidSubscription = new Subscription();
        activePaidSubscription.setUser(testUser);
        activePaidSubscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        activePaidSubscription.setPlan(Subscription.SubscriptionPlan.BASIC);

        activeFreeSubscription = new Subscription();
        activeFreeSubscription.setUser(testUser);
        activeFreeSubscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        activeFreeSubscription.setPlan(Subscription.SubscriptionPlan.FREE);
    }

    @Test
    @DisplayName("Should detect preview mode when user has no subscription")
    void shouldDetectPreviewModeWhenNoSubscription() {
        // Arrange
        when(costTrackingService.isPreviewMode(testUser)).thenReturn(true);

        // Act
        boolean isPreview = accessControlService.isPreviewMode(testUser);

        // Assert
        assertThat(isPreview).isTrue();
    }

    @Test
    @DisplayName("Should detect preview mode when user has FREE subscription")
    void shouldDetectPreviewModeWhenFreeSubscription() {
        // Arrange
        when(costTrackingService.isPreviewMode(testUser)).thenReturn(true);

        // Act
        boolean isPreview = accessControlService.isPreviewMode(testUser);

        // Assert
        assertThat(isPreview).isTrue();
    }

    @Test
    @DisplayName("Should not detect preview mode when user has paid subscription")
    void shouldNotDetectPreviewModeWhenPaidSubscription() {
        // Arrange
        when(costTrackingService.isPreviewMode(testUser)).thenReturn(false);

        // Act
        boolean isPreview = accessControlService.isPreviewMode(testUser);

        // Assert
        assertThat(isPreview).isFalse();
    }

    @Test
    @DisplayName("Should deny integration script access for preview mode users")
    void shouldDenyIntegrationScriptAccessForPreviewMode() {
        // Arrange
        when(subscriptionRepository.findByUserId(testUser.getId())).thenReturn(Optional.empty());

        // Act
        boolean canAccess = accessControlService.canAccessIntegrationScript(testUser);

        // Assert
        assertThat(canAccess).isFalse();
    }

    @Test
    @DisplayName("Should deny integration script access for FREE subscription")
    void shouldDenyIntegrationScriptAccessForFreeSubscription() {
        // Arrange
        when(subscriptionRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(activeFreeSubscription));

        // Act
        boolean canAccess = accessControlService.canAccessIntegrationScript(testUser);

        // Assert
        assertThat(canAccess).isFalse();
    }

    @Test
    @DisplayName("Should allow integration script access for paid subscription")
    void shouldAllowIntegrationScriptAccessForPaidSubscription() {
        // Arrange
        when(subscriptionRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(activePaidSubscription));

        // Act
        boolean canAccess = accessControlService.canAccessIntegrationScript(testUser);

        // Assert
        assertThat(canAccess).isTrue();
    }

    @Test
    @DisplayName("Should deny integration script access for inactive paid subscription")
    void shouldDenyIntegrationScriptAccessForInactivePaidSubscription() {
        // Arrange
        activePaidSubscription.setStatus(Subscription.SubscriptionStatus.CANCELED);
        when(subscriptionRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(activePaidSubscription));

        // Act
        boolean canAccess = accessControlService.canAccessIntegrationScript(testUser);

        // Assert
        assertThat(canAccess).isFalse();
    }

    @Test
    @DisplayName("Should allow chatbot creation for FREE plan when under limit (0 chatbots)")
    void shouldAllowChatbotCreationForPreviewModeWithZeroChatbots() {
        when(subscriptionRepository.findByUserId(testUser.getId())).thenReturn(Optional.empty());
        assertThat(accessControlService.canCreateChatbot(testUser, 0)).isTrue();
    }

    @Test
    @DisplayName("Should deny chatbot creation for FREE plan when at limit (1 chatbot)")
    void shouldDenyChatbotCreationForFreePlanWhenAtLimit() {
        when(subscriptionRepository.findByUserId(testUser.getId())).thenReturn(Optional.empty());
        assertThat(accessControlService.canCreateChatbot(testUser, 1)).isFalse();
    }

    @Test
    @DisplayName("Should deny chatbot creation for FREE plan when over limit (2 chatbots)")
    void shouldAllowChatbotCreationForPreviewModeWithTwoChatbots() {
        when(subscriptionRepository.findByUserId(testUser.getId())).thenReturn(Optional.empty());
        assertThat(accessControlService.canCreateChatbot(testUser, 2)).isFalse();
    }

    @Test
    @DisplayName("Should deny chatbot creation for FREE plan when over limit (3 chatbots)")
    void shouldDenyChatbotCreationForPreviewModeWithThreeChatbots() {
        when(subscriptionRepository.findByUserId(testUser.getId())).thenReturn(Optional.empty());
        assertThat(accessControlService.canCreateChatbot(testUser, 3)).isFalse();
    }

    @Test
    @DisplayName("Should allow chatbot creation for paid users when at 0 chatbots")
    void shouldAllowChatbotCreationForPaidUsers() {
        when(subscriptionRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(activePaidSubscription));
        assertThat(accessControlService.canCreateChatbot(testUser, 0)).isTrue();
    }

    @Test
    @DisplayName("Should deny chatbot creation when user already has 1 chatbot (one per user)")
    void shouldDenyChatbotCreationWhenAtLimit() {
        when(subscriptionRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(activePaidSubscription));
        assertThat(accessControlService.canCreateChatbot(testUser, 1)).isFalse();
    }

    @Test
    @DisplayName("Should return max 1 chatbot for FREE plan")
    void shouldReturnMaxOneChatbotForFreePlan() {
        when(subscriptionRepository.findByUserId(testUser.getId())).thenReturn(Optional.empty());

        int maxChatbots = accessControlService.getMaxChatbotsAllowed(testUser);

        assertThat(maxChatbots).isEqualTo(1);
    }

    @Test
    @DisplayName("Should return 1 chatbot for all plans (one chatbot per user)")
    void shouldReturnPlanBasedChatbotLimitForPaidUsers() {
        when(subscriptionRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(activePaidSubscription));

        int maxChatbots = accessControlService.getMaxChatbotsAllowed(testUser);

        assertThat(maxChatbots).isEqualTo(1);
    }

    @Test
    @DisplayName("Should check active subscription correctly")
    void shouldCheckActiveSubscription() {
        // Arrange
        when(subscriptionRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(activePaidSubscription));

        // Act
        boolean hasActive = accessControlService.hasActiveSubscription(testUser);

        // Assert
        assertThat(hasActive).isTrue();
    }

    @Test
    @DisplayName("Should return false for active subscription when no subscription exists")
    void shouldReturnFalseForActiveSubscriptionWhenNoSubscription() {
        // Arrange
        when(subscriptionRepository.findByUserId(testUser.getId())).thenReturn(Optional.empty());

        // Act
        boolean hasActive = accessControlService.hasActiveSubscription(testUser);

        // Assert
        assertThat(hasActive).isFalse();
    }
}

