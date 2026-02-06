package com.prayer_chat.chatbot.integration.security;

import com.prayer_chat.chatbot.config.TestOAuth2ClientRepositoryConfig;
import com.prayer_chat.chatbot.exception.ChatbotLimitReachedException;
import com.prayer_chat.chatbot.helpers.TestDataBuilder;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.repository.UserRepository;
import com.prayer_chat.chatbot.service.ChatbotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests to verify that chatbot creation enforces per-user limit under concurrent load.
 * SECURITY_AUDIT_PLAN Phase 1.2: "10 threads creating chatbots - verify limit is enforced"
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestOAuth2ClientRepositoryConfig.class)
@DisplayName("Chatbot creation concurrent security IT")
class ChatbotCreationConcurrentSecurityIT {

    private static final int THREAD_COUNT = 10;
    private static final int MAX_CHATBOTS_ALLOWED = 2;

    @Autowired
    private ChatbotService chatbotService;

    @Autowired
    private ChatbotRepository chatbotRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        chatbotRepository.deleteAll();
        userRepository.deleteAll();

        user = TestDataBuilder.createTestUser("concurrent-chatbot@example.com");
        user.setUsername("concurrentchatbot");
        user = userRepository.save(user);
    }

    @Test
    @DisplayName("Concurrent createChatbotEnforcingLimit allows at most maxAllowed chatbots")
    void concurrentCreateEnforcesLimit() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger(0);
        AtomicInteger limitReached = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    start.await();
                    Chatbot bot = TestDataBuilder.createTestChatbot(user);
                    bot.setName("Concurrent Bot " + index);
                    bot.setWebsiteUrl("https://example.com/" + index);
                    chatbotService.createChatbotEnforcingLimit(bot, user, MAX_CHATBOTS_ALLOWED);
                    created.incrementAndGet();
                } catch (ChatbotLimitReachedException e) {
                    limitReached.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);

        long count = chatbotRepository.countByOwner(user.getId());
        assertThat(count).isEqualTo(MAX_CHATBOTS_ALLOWED);
        assertThat(created.get()).isEqualTo(MAX_CHATBOTS_ALLOWED);
        assertThat(limitReached.get()).isEqualTo(THREAD_COUNT - MAX_CHATBOTS_ALLOWED);
    }
}
