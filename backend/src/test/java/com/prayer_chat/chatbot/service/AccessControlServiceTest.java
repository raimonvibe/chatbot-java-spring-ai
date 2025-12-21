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
    @DisplayName("Should allow chatbot creation for preview mode users with 0 chatbots")
    void shouldAllowChatbotCreationForPreviewModeWithZeroChatbots() {
        // Arrange
        when(costTrackingService.isPreviewMode(testUser)).thenReturn(true);

        // Act
        boolean canCreate = accessControlService.canCreateChatbot(testUser, 0);

        // Assert
        assertThat(canCreate).isTrue();
    }

    @Test
    @DisplayName("Should allow chatbot creation for preview mode users with 1 chatbot")
    void shouldAllowChatbotCreationForPreviewModeWithOneChatbot() {
        // Arrange
        when(costTrackingService.isPreviewMode(testUser)).thenReturn(true);

        // Act
        boolean canCreate = accessControlService.canCreateChatbot(testUser, 1);

        // Assert
        assertThat(canCreate).isTrue();
    }

    @Test
    @DisplayName("Should allow chatbot creation for preview mode users with 2 chatbots")
    void shouldAllowChatbotCreationForPreviewModeWithTwoChatbots() {
        // Arrange
        when(costTrackingService.isPreviewMode(testUser)).thenReturn(true);

        // Act
        boolean canCreate = accessControlService.canCreateChatbot(testUser, 2);

        // Assert
        assertThat(canCreate).isTrue();
    }

    @Test
    @DisplayName("Should deny chatbot creation for preview mode users with 3 chatbots")
    void shouldDenyChatbotCreationForPreviewModeWithThreeChatbots() {
        // Arrange
        when(costTrackingService.isPreviewMode(testUser)).thenReturn(true);

        // Act
        boolean canCreate = accessControlService.canCreateChatbot(testUser, 3);

        // Assert
        assertThat(canCreate).isFalse();
    }

    @Test
    @DisplayName("Should allow chatbot creation for paid users regardless of count")
    void shouldAllowChatbotCreationForPaidUsers() {
        // Arrange
        when(costTrackingService.isPreviewMode(testUser)).thenReturn(false);

        // Act
        boolean canCreate = accessControlService.canCreateChatbot(testUser, 5);

        // Assert
        assertThat(canCreate).isTrue();
    }

    @Test
    @DisplayName("Should return max 3 chatbots for preview mode users (temporary for testing)")
    void shouldReturnMaxThreeChatbotsForPreviewMode() {
        // Arrange
        when(costTrackingService.isPreviewMode(testUser)).thenReturn(true);

        // Act
        int maxChatbots = accessControlService.getMaxChatbotsAllowed(testUser);

        // Assert
        assertThat(maxChatbots).isEqualTo(3);
    }

    @Test
    @DisplayName("Should return unlimited chatbots for paid users")
    void shouldReturnUnlimitedChatbotsForPaidUsers() {
        // Arrange
        when(costTrackingService.isPreviewMode(testUser)).thenReturn(false);
        when(subscriptionRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(activePaidSubscription));

        // Act
        int maxChatbots = accessControlService.getMaxChatbotsAllowed(testUser);

        // Assert
        assertThat(maxChatbots).isEqualTo(Integer.MAX_VALUE);
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

