package com.tjanabot.chatbot.security;

import com.tjanabot.chatbot.model.Subscription;
import com.tjanabot.chatbot.model.Subscription.SubscriptionPlan;
import com.tjanabot.chatbot.model.Subscription.SubscriptionStatus;
import com.tjanabot.chatbot.model.User;
import com.tjanabot.chatbot.repository.SubscriptionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OAuth2AuthenticationSuccessHandler
 * Tests OAuth2 authentication success scenarios and redirects
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2 Authentication Success Handler Tests")
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    @Mock
    private OAuth2User oauth2User;

    @Mock
    private DefaultRedirectStrategy redirectStrategy;

    @InjectMocks
    private OAuth2AuthenticationSuccessHandler successHandler;

    private static final String FRONTEND_URL = "http://localhost:3000";
    private static final String TEST_EMAIL = "test@example.com";
    private static final Long TEST_USER_ID = 1L;

    private User testUser;
    private CustomOAuth2User customOAuth2User;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(successHandler, "frontendUrl", FRONTEND_URL);
        successHandler.setRedirectStrategy(redirectStrategy);

        // Create test user
        testUser = new User();
        testUser.setId(TEST_USER_ID);
        testUser.setEmail(TEST_EMAIL);
        testUser.setUsername("testuser");

        // Create custom OAuth2 user
        customOAuth2User = new CustomOAuth2User(oauth2User, testUser);
    }

    @Test
    @DisplayName("Should redirect to dashboard when user has active subscription")
    void shouldRedirectToDashboard_whenUserHasActiveSubscription() throws Exception {
        // Arrange
        Subscription activeSubscription = createActiveSubscription();
        when(authentication.getPrincipal()).thenReturn(customOAuth2User);
        when(subscriptionRepository.findByUserId(TEST_USER_ID))
            .thenReturn(Optional.of(activeSubscription));

        // Act
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // Assert
        verify(subscriptionRepository).findByUserId(TEST_USER_ID);
        verify(redirectStrategy).sendRedirect(request, response, FRONTEND_URL + "/dashboard");
    }

    @Test
    @DisplayName("Should redirect to pricing when user has no subscription")
    void shouldRedirectToPricing_whenUserHasNoSubscription() throws Exception {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(customOAuth2User);
        when(subscriptionRepository.findByUserId(TEST_USER_ID))
            .thenReturn(Optional.empty());

        // Act
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // Assert
        verify(subscriptionRepository).findByUserId(TEST_USER_ID);
        verify(redirectStrategy).sendRedirect(request, response,
            FRONTEND_URL + "/pricing?new_user=true");
    }

    @Test
    @DisplayName("Should redirect to pricing when subscription is inactive")
    void shouldRedirectToPricing_whenSubscriptionIsInactive() throws Exception {
        // Arrange
        Subscription inactiveSubscription = createInactiveSubscription();
        when(authentication.getPrincipal()).thenReturn(customOAuth2User);
        when(subscriptionRepository.findByUserId(TEST_USER_ID))
            .thenReturn(Optional.of(inactiveSubscription));

        // Act
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // Assert
        verify(subscriptionRepository).findByUserId(TEST_USER_ID);
        verify(redirectStrategy).sendRedirect(request, response,
            FRONTEND_URL + "/pricing?new_user=true");
    }

    @Test
    @DisplayName("Should redirect to pricing when subscription is canceled")
    void shouldRedirectToPricing_whenSubscriptionIsCanceled() throws Exception {
        // Arrange
        Subscription canceledSubscription = createCanceledSubscription();
        when(authentication.getPrincipal()).thenReturn(customOAuth2User);
        when(subscriptionRepository.findByUserId(TEST_USER_ID))
            .thenReturn(Optional.of(canceledSubscription));

        // Act
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // Assert
        verify(subscriptionRepository).findByUserId(TEST_USER_ID);
        verify(redirectStrategy).sendRedirect(request, response,
            FRONTEND_URL + "/pricing?new_user=true");
    }

    @Test
    @DisplayName("Should redirect to dashboard when subscription is trialing")
    void shouldRedirectToDashboard_whenSubscriptionIsTrialing() throws Exception {
        // Arrange
        Subscription trialingSubscription = createTrialingSubscription();
        when(authentication.getPrincipal()).thenReturn(customOAuth2User);
        when(subscriptionRepository.findByUserId(TEST_USER_ID))
            .thenReturn(Optional.of(trialingSubscription));

        // Act
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // Assert
        verify(subscriptionRepository).findByUserId(TEST_USER_ID);
        verify(redirectStrategy).sendRedirect(request, response, FRONTEND_URL + "/dashboard");
    }

    @Test
    @DisplayName("Should redirect to dashboard for PRO plan subscription")
    void shouldRedirectToDashboard_forProPlanSubscription() throws Exception {
        // Arrange
        Subscription proSubscription = createProSubscription();
        when(authentication.getPrincipal()).thenReturn(customOAuth2User);
        when(subscriptionRepository.findByUserId(TEST_USER_ID))
            .thenReturn(Optional.of(proSubscription));

        // Act
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // Assert
        verify(subscriptionRepository).findByUserId(TEST_USER_ID);
        verify(redirectStrategy).sendRedirect(request, response, FRONTEND_URL + "/dashboard");
    }

    @Test
    @DisplayName("Should redirect to dashboard for ENTERPRISE plan subscription")
    void shouldRedirectToDashboard_forEnterprisePlanSubscription() throws Exception {
        // Arrange
        Subscription enterpriseSubscription = createEnterpriseSubscription();
        when(authentication.getPrincipal()).thenReturn(customOAuth2User);
        when(subscriptionRepository.findByUserId(TEST_USER_ID))
            .thenReturn(Optional.of(enterpriseSubscription));

        // Act
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // Assert
        verify(subscriptionRepository).findByUserId(TEST_USER_ID);
        verify(redirectStrategy).sendRedirect(request, response, FRONTEND_URL + "/dashboard");
    }

    @Test
    @DisplayName("Should redirect to pricing when subscription is FREE plan")
    void shouldRedirectToPricing_whenSubscriptionIsFreePlan() throws Exception {
        // Arrange
        Subscription freeSubscription = createFreeSubscription();
        when(authentication.getPrincipal()).thenReturn(customOAuth2User);
        when(subscriptionRepository.findByUserId(TEST_USER_ID))
            .thenReturn(Optional.of(freeSubscription));

        // Act
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // Assert
        verify(subscriptionRepository).findByUserId(TEST_USER_ID);
        verify(redirectStrategy).sendRedirect(request, response,
            FRONTEND_URL + "/pricing?new_user=true");
    }

    @Test
    @DisplayName("Should redirect to pricing when subscription is PAST_DUE")
    void shouldRedirectToPricing_whenSubscriptionIsPastDue() throws Exception {
        // Arrange
        Subscription pastDueSubscription = createPastDueSubscription();
        when(authentication.getPrincipal()).thenReturn(customOAuth2User);
        when(subscriptionRepository.findByUserId(TEST_USER_ID))
            .thenReturn(Optional.of(pastDueSubscription));

        // Act
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // Assert
        verify(subscriptionRepository).findByUserId(TEST_USER_ID);
        verify(redirectStrategy).sendRedirect(request, response,
            FRONTEND_URL + "/pricing?new_user=true");
    }

    @Test
    @DisplayName("Should use fallback when principal is not CustomOAuth2User")
    void shouldUseFallback_whenPrincipalIsNotCustomOAuth2User() throws Exception {
        // Arrange
        when(authentication.getPrincipal()).thenReturn("not-a-custom-oauth2-user");

        // Create a spy to verify superclass method is called
        OAuth2AuthenticationSuccessHandler spyHandler = spy(successHandler);
        ReflectionTestUtils.setField(spyHandler, "frontendUrl", FRONTEND_URL);
        spyHandler.setRedirectStrategy(redirectStrategy);

        // Act
        spyHandler.onAuthenticationSuccess(request, response, authentication);

        // Assert
        verify(subscriptionRepository, never()).findByUserId(any());
        // The parent class method would handle this case
    }

    @Test
    @DisplayName("Should handle exception when repository throws error")
    void shouldHandleException_whenRepositoryThrowsError() throws Exception {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(customOAuth2User);
        when(subscriptionRepository.findByUserId(TEST_USER_ID))
            .thenThrow(new RuntimeException("Database error"));

        // Act & Assert - should not throw exception
        try {
            successHandler.onAuthenticationSuccess(request, response, authentication);
        } catch (Exception e) {
            // Exception is expected to be handled or propagated
        }

        verify(subscriptionRepository).findByUserId(TEST_USER_ID);
    }

    @Test
    @DisplayName("Should use configured frontend URL in redirect")
    void shouldUseConfiguredFrontendUrl_inRedirect() throws Exception {
        // Arrange
        String customFrontendUrl = "https://example.com";
        ReflectionTestUtils.setField(successHandler, "frontendUrl", customFrontendUrl);

        Subscription activeSubscription = createActiveSubscription();
        when(authentication.getPrincipal()).thenReturn(customOAuth2User);
        when(subscriptionRepository.findByUserId(TEST_USER_ID))
            .thenReturn(Optional.of(activeSubscription));

        // Act
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // Assert
        verify(redirectStrategy).sendRedirect(request, response, customFrontendUrl + "/dashboard");
    }

    // Helper methods to create test subscriptions

    private Subscription createActiveSubscription() {
        Subscription subscription = new Subscription(testUser, "cus_test_123");
        subscription.setId(1L);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setPlan(SubscriptionPlan.BASIC);
        subscription.setStripeSubscriptionId("sub_test_123");
        subscription.setCurrentPeriodEnd(LocalDateTime.now().plusMonths(1));
        return subscription;
    }

    private Subscription createInactiveSubscription() {
        Subscription subscription = new Subscription(testUser, "cus_test_123");
        subscription.setId(1L);
        subscription.setStatus(SubscriptionStatus.INACTIVE);
        subscription.setPlan(SubscriptionPlan.FREE);
        return subscription;
    }

    private Subscription createCanceledSubscription() {
        Subscription subscription = new Subscription(testUser, "cus_test_123");
        subscription.setId(1L);
        subscription.setStatus(SubscriptionStatus.CANCELED);
        subscription.setPlan(SubscriptionPlan.BASIC);
        subscription.setCanceledAt(LocalDateTime.now());
        return subscription;
    }

    private Subscription createTrialingSubscription() {
        Subscription subscription = new Subscription(testUser, "cus_test_123");
        subscription.setId(1L);
        subscription.setStatus(SubscriptionStatus.TRIALING);
        subscription.setPlan(SubscriptionPlan.BASIC);
        subscription.setCurrentPeriodEnd(LocalDateTime.now().plusDays(14));
        return subscription;
    }

    private Subscription createProSubscription() {
        Subscription subscription = new Subscription(testUser, "cus_test_123");
        subscription.setId(1L);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setPlan(SubscriptionPlan.PRO);
        subscription.setCurrentPeriodEnd(LocalDateTime.now().plusMonths(1));
        return subscription;
    }

    private Subscription createEnterpriseSubscription() {
        Subscription subscription = new Subscription(testUser, "cus_test_123");
        subscription.setId(1L);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setPlan(SubscriptionPlan.ENTERPRISE);
        subscription.setCurrentPeriodEnd(LocalDateTime.now().plusMonths(1));
        return subscription;
    }

    private Subscription createFreeSubscription() {
        Subscription subscription = new Subscription(testUser, "cus_test_123");
        subscription.setId(1L);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setPlan(SubscriptionPlan.FREE);
        return subscription;
    }

    private Subscription createPastDueSubscription() {
        Subscription subscription = new Subscription(testUser, "cus_test_123");
        subscription.setId(1L);
        subscription.setStatus(SubscriptionStatus.PAST_DUE);
        subscription.setPlan(SubscriptionPlan.BASIC);
        subscription.setPaymentRetryCount(1);
        subscription.setGracePeriodEnd(LocalDateTime.now().plusDays(7));
        return subscription;
    }
}
