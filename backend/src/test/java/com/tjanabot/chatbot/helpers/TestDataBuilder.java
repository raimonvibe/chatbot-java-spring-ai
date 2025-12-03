package com.tjanabot.chatbot.helpers;

import com.tjanabot.chatbot.model.*;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Helper class for building test data objects
 * Provides methods to create valid test entities with reasonable defaults
 */
public class TestDataBuilder {

    /**
     * Create a test user with random email
     */
    public static User createTestUser() {
        User user = new User();
        user.setEmail("test" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        user.setUsername("testuser" + UUID.randomUUID().toString().substring(0, 8));
        user.setPassword(BCrypt.hashpw("Test1234!", BCrypt.gensalt()));
        user.setAuthProvider(User.AuthProvider.LOCAL);
        return user;
    }

    /**
     * Create a test user with specific email
     */
    public static User createTestUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setUsername("testuser");
        user.setPassword(BCrypt.hashpw("Test1234!", BCrypt.gensalt()));
        user.setAuthProvider(User.AuthProvider.LOCAL);
        return user;
    }

    /**
     * Create a test user with Google auth provider
     */
    public static User createGoogleUser(String email, String googleId) {
        User user = new User();
        user.setEmail(email);
        user.setUsername("googleuser");
        user.setGoogleId(googleId);
        user.setAuthProvider(User.AuthProvider.GOOGLE);
        return user;
    }

    /**
     * Create a test chatbot
     */
    public static Chatbot createTestChatbot(User owner) {
        Chatbot chatbot = new Chatbot();
        chatbot.setName("Test Bot " + UUID.randomUUID().toString().substring(0, 8));
        chatbot.setDescription("Test chatbot for unit testing");
        chatbot.setOwner(owner);
        chatbot.setPrimaryLanguage("en");
        chatbot.setCustomPrompt("You are a helpful assistant.");
        chatbot.setWebsiteUrl("https://example.com");
        chatbot.setActive(true);
        return chatbot;
    }

    /**
     * Create an active subscription for a user
     */
    public static Subscription createActiveSubscription(User user) {
        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setStripeCustomerId("cus_test_" + UUID.randomUUID().toString().substring(0, 8));
        subscription.setStripeSubscriptionId("sub_test_" + UUID.randomUUID().toString().substring(0, 8));
        subscription.setStripePriceId("price_test_123");
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription.setPlan(Subscription.SubscriptionPlan.BASIC);
        subscription.setCurrentPeriodStart(LocalDateTime.now());
        subscription.setCurrentPeriodEnd(LocalDateTime.now().plusMonths(1));
        subscription.setPaymentRetryCount(0);
        return subscription;
    }

    /**
     * Create a subscription with specific status
     */
    public static Subscription createSubscription(User user, Subscription.SubscriptionStatus status) {
        Subscription subscription = createActiveSubscription(user);
        subscription.setStatus(status);
        return subscription;
    }

    /**
     * Create a subscription with payment failures
     */
    public static Subscription createSubscriptionWithFailures(User user, int failureCount) {
        Subscription subscription = createActiveSubscription(user);
        subscription.setStatus(Subscription.SubscriptionStatus.PAST_DUE);
        subscription.setPaymentRetryCount(failureCount);
        subscription.setLastPaymentAttempt(LocalDateTime.now().minusHours(1));
        subscription.setGracePeriodEnd(LocalDateTime.now().plusDays(7));
        return subscription;
    }

    /**
     * Create an audit log entry
     */
    public static AuditLog createAuditLog(User user, AuditLog.EventType eventType) {
        return new AuditLog.Builder(eventType, "Test action")
            .user(user)
            .severity(AuditLog.Severity.INFO)
            .description("Test audit log entry")
            .build();
    }

    /**
     * Create an audit log with specific severity
     */
    public static AuditLog createAuditLog(User user, AuditLog.EventType eventType,
                                          AuditLog.Severity severity) {
        return new AuditLog.Builder(eventType, "Test action")
            .user(user)
            .severity(severity)
            .description("Test audit log entry")
            .build();
    }

    /**
     * Create a conversation
     */
    public static Conversation createConversation(Chatbot chatbot) {
        Conversation conversation = new Conversation();
        conversation.setChatbot(chatbot);
        conversation.setSessionId(UUID.randomUUID().toString());
        conversation.setStartedAt(LocalDateTime.now());
        conversation.setLastMessageAt(LocalDateTime.now());
        return conversation;
    }

    /**
     * Create a message
     */
    public static Message createMessage(Conversation conversation, Message.Sender sender, String content) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(content);
        message.setLanguage("en");
        message.setTimestamp(LocalDateTime.now());
        return message;
    }
}
