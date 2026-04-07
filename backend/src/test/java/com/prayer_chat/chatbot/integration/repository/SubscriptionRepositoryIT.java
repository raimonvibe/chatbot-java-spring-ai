package com.prayer_chat.chatbot.integration.repository;

import com.prayer_chat.chatbot.helpers.TestDataBuilder;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import com.prayer_chat.chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@ContextConfiguration(classes = com.prayer_chat.chatbot.AiChatbotApplication.class)
@AutoConfigureTestDatabase(replace = org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("SubscriptionRepository Integration Tests")
class SubscriptionRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        subscriptionRepository.deleteAll();
        userRepository.deleteAll();

        testUser = TestDataBuilder.createTestUser();
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("Should save and find subscription by user ID")
    void shouldSaveAndFindByUserId() {
        // Arrange
        Subscription subscription = TestDataBuilder.createActiveSubscription(testUser);

        // Act
        Subscription saved = subscriptionRepository.save(subscription);
        Optional<Subscription> found = subscriptionRepository.findByUserId(testUser.getId());

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(found).isPresent();
        assertThat(found.get().getUser().getId()).isEqualTo(testUser.getId());
        assertThat(found.get().getStatus()).isEqualTo(Subscription.SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should find subscription by Stripe subscription ID")
    void shouldFindByStripeSubscriptionId() {
        // Arrange
        Subscription subscription = TestDataBuilder.createActiveSubscription(testUser);
        subscription.setStripeSubscriptionId("sub_test_unique_123");
        subscriptionRepository.save(subscription);

        // Act
        Optional<Subscription> found =
            subscriptionRepository.findByStripeSubscriptionId("sub_test_unique_123");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getStripeSubscriptionId()).isEqualTo("sub_test_unique_123");
    }

    @Test
    @DisplayName("Should find subscription by Stripe customer ID")
    void shouldFindByStripeCustomerId() {
        // Arrange
        Subscription subscription = TestDataBuilder.createActiveSubscription(testUser);
        subscription.setStripeCustomerId("cus_test_unique_456");
        subscriptionRepository.save(subscription);

        // Act
        Optional<Subscription> found =
            subscriptionRepository.findByStripeCustomerId("cus_test_unique_456");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getStripeCustomerId()).isEqualTo("cus_test_unique_456");
    }

    @Test
    @DisplayName("Should count payment failures for user")
    void shouldCountPaymentFailures() {
        // Arrange
        Subscription subscription = TestDataBuilder.createSubscriptionWithFailures(testUser, 2);
        subscriptionRepository.save(subscription);

        // Act
        Optional<Subscription> found = subscriptionRepository.findByUserId(testUser.getId());

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getPaymentRetryCount()).isEqualTo(2);
        assertThat(found.get().getGracePeriodEnd()).isNotNull();
        assertThat(found.get().getStatus()).isEqualTo(Subscription.SubscriptionStatus.PAST_DUE);
    }

    @Test
    @DisplayName("Should update subscription status")
    void shouldUpdateSubscriptionStatus() {
        // Arrange
        Subscription subscription = TestDataBuilder.createActiveSubscription(testUser);
        subscription = subscriptionRepository.save(subscription);

        // Act
        subscription.setStatus(Subscription.SubscriptionStatus.CANCELED);
        subscription.setCanceledAt(LocalDateTime.now());
        Subscription updated = subscriptionRepository.save(subscription);

        // Assert
        assertThat(updated.getStatus()).isEqualTo(Subscription.SubscriptionStatus.CANCELED);
        assertThat(updated.getCanceledAt()).isNotNull();
    }

    @Test
    @DisplayName("Should track payment retry attempts")
    void shouldTrackPaymentRetryAttempts() {
        // Arrange
        Subscription subscription = TestDataBuilder.createActiveSubscription(testUser);
        subscription = subscriptionRepository.save(subscription);

        // Act - Simulate payment failures
        subscription.setPaymentRetryCount(1);
        subscription.setLastPaymentAttempt(LocalDateTime.now());
        subscription.setStatus(Subscription.SubscriptionStatus.PAST_DUE);
        subscription = subscriptionRepository.save(subscription);

        // Assert
        Optional<Subscription> found = subscriptionRepository.findByUserId(testUser.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getPaymentRetryCount()).isEqualTo(1);
        assertThat(found.get().getLastPaymentAttempt()).isNotNull();
        assertThat(found.get().getStatus()).isEqualTo(Subscription.SubscriptionStatus.PAST_DUE);
    }

    @Test
    @DisplayName("Should enforce one subscription per user constraint")
    void shouldEnforceOneSubscriptionPerUser() {
        // Arrange
        Subscription subscription1 = TestDataBuilder.createActiveSubscription(testUser);
        subscriptionRepository.save(subscription1);

        // Act & Assert - Try to create second subscription for same user
        Subscription subscription2 = TestDataBuilder.createActiveSubscription(testUser);

        // This should violate unique constraint on user_id
        assertThatThrownBy(() -> {
            subscriptionRepository.save(subscription2);
            subscriptionRepository.flush(); // Force constraint check
        }).isInstanceOf(Exception.class); // DataIntegrityViolationException or similar
    }
}
