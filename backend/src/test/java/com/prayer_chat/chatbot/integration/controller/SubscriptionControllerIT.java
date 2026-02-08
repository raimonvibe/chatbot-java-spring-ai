package com.prayer_chat.chatbot.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.StripeException;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import com.prayer_chat.chatbot.repository.UserRepository;
import com.prayer_chat.chatbot.security.JwtTokenProvider;
import com.prayer_chat.chatbot.service.StripeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.prayer_chat.chatbot.config.MockAiConfiguration;
import com.prayer_chat.chatbot.config.TestSecurityConfig;
import com.prayer_chat.chatbot.config.TestJacksonConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for SubscriptionController
 *
 * Tests covered:
 * - Subscription status checks
 * - Plan changes (upgrades and downgrades)
 * - Subscription cancellation
 * - Free tier handling
 */
@SpringBootTest(classes = com.prayer_chat.chatbot.AiChatbotApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MockAiConfiguration.class, TestSecurityConfig.class, TestJacksonConfiguration.class})
@DisplayName("SubscriptionController Integration Tests")
class SubscriptionControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StripeService stripeService;

    @MockBean
    private SubscriptionRepository subscriptionRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private User testUser;
    private Subscription testSubscription;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("sub@example.com");
        testUser.setUsername("subuser");

        testSubscription = new Subscription();
        testSubscription.setId(100L);
        testSubscription.setUser(testUser);
        testSubscription.setPlan(Subscription.SubscriptionPlan.FREE);
        testSubscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        testSubscription.setStripeCustomerId("cus_test_123");
        testSubscription.setCurrentPeriodStart(LocalDateTime.now());
        testSubscription.setCurrentPeriodEnd(LocalDateTime.now().plusMonths(1));

        // Mock JWT token validation
        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(anyString())).thenReturn("sub@example.com");
        when(userRepository.findByEmail("sub@example.com")).thenReturn(Optional.of(testUser));
    }

    /**
     * Helper method to create CustomOAuth2User authentication for MockMvc tests
     */
    private org.springframework.security.core.Authentication createCustomOAuth2UserAuthentication(User user) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "test_" + user.getId());
        attributes.put("email", user.getEmail());
        attributes.put("name", user.getUsername());

        OAuth2User oauth2User = new DefaultOAuth2User(
            Collections.emptyList(),
            attributes,
            "email"
        );

        CustomOAuth2User customOAuth2User = new CustomOAuth2User(oauth2User, user);
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            customOAuth2User,
            null,
            customOAuth2User.getAuthorities()
        );
    }

    @Test
    @DisplayName("Should get subscription status for user")
    void shouldGetSubscriptionStatus() throws Exception {
        // Arrange
        when(subscriptionRepository.findByUserId(1L))
            .thenReturn(Optional.of(testSubscription));

        // Act & Assert
        mockMvc.perform(get("/api/subscription/status")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hasSubscription", equalTo(true)))
            .andExpect(jsonPath("$.plan", equalTo("FREE")))
            .andExpect(jsonPath("$.isActive", equalTo(true)));

        verify(subscriptionRepository, times(1)).findByUserId(1L);
    }

    @Test
    @DisplayName("Should return no subscription when user has no subscription")
    void shouldReturnNoSubscriptionWhenUserHasNone() throws Exception {
        // Arrange
        when(subscriptionRepository.findByUserId(1L))
            .thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/subscription/status")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hasSubscription", equalTo(false)))
            .andExpect(jsonPath("$.isActive", equalTo(false)));
    }

    @Test
    @DisplayName("Should create checkout session")
    void shouldCreateCheckoutSession() throws Exception {
        // Arrange
        when(stripeService.isConfigured()).thenReturn(true);
        when(subscriptionRepository.findByUserId(1L))
            .thenReturn(Optional.empty());
        when(stripeService.createCheckoutSession(any(User.class), any()))
            .thenReturn("https://checkout.stripe.com/test");

        // Act & Assert
        mockMvc.perform(post("/api/subscription/create-checkout-session")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkoutUrl", equalTo("https://checkout.stripe.com/test")));

        verify(stripeService, times(1)).createCheckoutSession(any(User.class), any());
    }

    @Test
    @DisplayName("Should create checkout session with plan PRO")
    void shouldCreateCheckoutSession_withPlan() throws Exception {
        when(stripeService.isConfigured()).thenReturn(true);
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(stripeService.createCheckoutSession(any(User.class), eq("PRO")))
            .thenReturn("https://checkout.stripe.com/pro");

        mockMvc.perform(post("/api/subscription/create-checkout-session")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\": \"PRO\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkoutUrl", equalTo("https://checkout.stripe.com/pro")));

        verify(stripeService, times(1)).createCheckoutSession(any(User.class), eq("PRO"));
    }

    @Test
    @DisplayName("Should return 400 for invalid plan in create checkout session")
    void shouldReturn400ForInvalidPlan_createCheckoutSession() throws Exception {
        when(stripeService.isConfigured()).thenReturn(true);
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/subscription/create-checkout-session")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\": \"INVALID\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("Invalid plan")));

        verify(stripeService, never()).createCheckoutSession(any(User.class), any());
    }

    @Test
    @DisplayName("Should return 503 when Stripe not configured for checkout")
    void shouldReturn503_whenStripeNotConfigured_createCheckoutSession() throws Exception {
        when(stripeService.isConfigured()).thenReturn(false);
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/subscription/create-checkout-session")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error", equalTo("Payment provider not configured")));

        verify(stripeService, never()).createCheckoutSession(any(User.class), any());
    }

    @Test
    @DisplayName("Should return 401 when unauthenticated for create checkout session")
    void shouldReturn401_whenUnauthenticated_createCheckoutSession() throws Exception {
        mockMvc.perform(post("/api/subscription/create-checkout-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 400 when user already has active subscription")
    void shouldReturn400_whenUserAlreadyHasActiveSubscription() throws Exception {
        testSubscription.setPlan(Subscription.SubscriptionPlan.BASIC);
        testSubscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        when(stripeService.isConfigured()).thenReturn(true);
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.of(testSubscription));

        mockMvc.perform(post("/api/subscription/create-checkout-session")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", equalTo("User already has an active subscription")));

        verify(stripeService, never()).createCheckoutSession(any(User.class), any());
    }

    @Test
    @DisplayName("Should create portal session and return portalUrl")
    void shouldCreatePortalSession_andReturnPortalUrl() throws Exception {
        when(stripeService.isConfigured()).thenReturn(true);
        when(stripeService.createBillingPortalSession(any(User.class), any()))
            .thenReturn("https://billing.stripe.com/session/test");

        mockMvc.perform(post("/api/subscription/create-portal-session")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.portalUrl", equalTo("https://billing.stripe.com/session/test")));

        verify(stripeService, times(1)).createBillingPortalSession(any(User.class), any());
    }

    @Test
    @DisplayName("Should create portal session with returnUrl in body (allowed origin)")
    void shouldCreatePortalSession_withReturnUrlInBody() throws Exception {
        when(stripeService.isConfigured()).thenReturn(true);
        String allowedReturnUrl = "http://localhost:3000/dashboard";
        when(stripeService.createBillingPortalSession(any(User.class), eq(allowedReturnUrl)))
            .thenReturn("https://billing.stripe.com/session/with-return");

        mockMvc.perform(post("/api/subscription/create-portal-session")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"returnUrl\": \"" + allowedReturnUrl + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.portalUrl", equalTo("https://billing.stripe.com/session/with-return")));

        verify(stripeService, times(1)).createBillingPortalSession(any(User.class), eq(allowedReturnUrl));
    }

    @Test
    @DisplayName("Should return 503 when Stripe not configured for portal session")
    void shouldReturn503_whenStripeNotConfigured_createPortalSession() throws Exception {
        when(stripeService.isConfigured()).thenReturn(false);

        mockMvc.perform(post("/api/subscription/create-portal-session")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error", equalTo("Payment provider not configured")));

        verify(stripeService, never()).createBillingPortalSession(any(User.class), any());
    }

    @Test
    @DisplayName("Should return 401 when unauthenticated for create portal session")
    void shouldReturn401_whenUnauthenticated_createPortalSession() throws Exception {
        mockMvc.perform(post("/api/subscription/create-portal-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 400 when portal returnUrl is not allowed (open redirect prevention)")
    void shouldReturn400_whenPortalReturnUrlNotAllowed() throws Exception {
        when(stripeService.isConfigured()).thenReturn(true);

        mockMvc.perform(post("/api/subscription/create-portal-session")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"returnUrl\": \"https://evil.com/phishing\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", equalTo("Invalid return URL")));

        verify(stripeService, never()).createBillingPortalSession(any(User.class), any());
    }

    @Test
    @DisplayName("Should cancel active subscription")
    void shouldCancelActiveSubscription() throws Exception {
        // Arrange
        testSubscription.setPlan(Subscription.SubscriptionPlan.BASIC);
        testSubscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        doNothing().when(stripeService).cancelSubscription(1L);

        // Act & Assert
        mockMvc.perform(post("/api/subscription/cancel")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message", equalTo("Subscription canceled successfully")));

        verify(stripeService, times(1)).cancelSubscription(1L);
    }

    @Test
    @DisplayName("Should get subscription details")
    void shouldGetSubscriptionDetails() throws Exception {
        // Arrange
        when(subscriptionRepository.findByUserId(1L))
            .thenReturn(Optional.of(testSubscription));

        // Act & Assert
        mockMvc.perform(get("/api/subscription/details")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", equalTo(100)))
            .andExpect(jsonPath("$.plan", equalTo("FREE")));
    }

    @Test
    @DisplayName("Should change subscription plan")
    void shouldChangeSubscriptionPlan() throws Exception {
        // Arrange
        doNothing().when(stripeService).changeSubscriptionPlan(
            eq(1L), eq("price_test"), eq(Subscription.SubscriptionPlan.BASIC));

        // Act & Assert
        mockMvc.perform(post("/api/subscription/change-plan")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"priceId\": \"price_test\", \"plan\": \"BASIC\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message", containsString("changed successfully")));

        verify(stripeService, times(1)).changeSubscriptionPlan(
            eq(1L), eq("price_test"), eq(Subscription.SubscriptionPlan.BASIC));
    }

    @Test
    @DisplayName("Should upgrade subscription plan")
    void shouldUpgradeSubscriptionPlan() throws Exception {
        // Arrange
        doNothing().when(stripeService).upgradeSubscription(
            eq(1L), eq("price_test"), eq(Subscription.SubscriptionPlan.PRO));

        // Act & Assert
        mockMvc.perform(post("/api/subscription/upgrade")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"priceId\": \"price_test\", \"plan\": \"PRO\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message", containsString("upgraded successfully")));

        verify(stripeService, times(1)).upgradeSubscription(
            eq(1L), eq("price_test"), eq(Subscription.SubscriptionPlan.PRO));
    }

    @Test
    @DisplayName("Should downgrade subscription plan")
    void shouldDowngradeSubscriptionPlan() throws Exception {
        // Arrange
        doNothing().when(stripeService).downgradeSubscription(
            eq(1L), eq("price_test"), eq(Subscription.SubscriptionPlan.BASIC));

        // Act & Assert
        mockMvc.perform(post("/api/subscription/downgrade")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"priceId\": \"price_test\", \"plan\": \"BASIC\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message", containsString("downgraded")));

        verify(stripeService, times(1)).downgradeSubscription(
            eq(1L), eq("price_test"), eq(Subscription.SubscriptionPlan.BASIC));
    }

    @Test
    @DisplayName("Should return 400 for invalid priceId in create checkout session")
    void shouldReturn400ForInvalidPriceId_createCheckoutSession() throws Exception {
        when(stripeService.isConfigured()).thenReturn(true);
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/subscription/create-checkout-session")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"priceId\": \"invalid_price_id\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", equalTo("Invalid price ID")));

        verify(stripeService, never()).createCheckoutSession(any(User.class), any());
    }

    @Test
    @DisplayName("Should return 400 for missing priceId in change plan")
    void shouldReturn400ForMissingPriceId() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/subscription/change-plan")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\": \"BASIC\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("Missing required fields")));
    }

    @Test
    @DisplayName("Should return 400 for invalid plan")
    void shouldReturn400ForInvalidPlan() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/subscription/change-plan")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"priceId\": \"price_test\", \"plan\": \"INVALID\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("Invalid plan")));
    }

    @Test
    @DisplayName("Should handle Stripe exception when canceling")
    void shouldHandleStripeExceptionWhenCanceling() throws Exception {
        // Arrange
        StripeException stripeException = mock(StripeException.class);
        doThrow(stripeException)
            .when(stripeService).cancelSubscription(1L);

        // Act & Assert
        mockMvc.perform(post("/api/subscription/cancel")
                .with(authentication(createCustomOAuth2UserAuthentication(testUser))))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error", equalTo("Failed to cancel subscription")));
    }
}
