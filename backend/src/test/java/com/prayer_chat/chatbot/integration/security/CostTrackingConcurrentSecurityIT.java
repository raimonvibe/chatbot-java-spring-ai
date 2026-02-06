package com.prayer_chat.chatbot.integration.security;

import com.prayer_chat.chatbot.config.TestOAuth2ClientRepositoryConfig;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.AuditLogRepository;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import com.prayer_chat.chatbot.repository.UserRepository;
import com.prayer_chat.chatbot.service.CostTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests to verify that cost tracking enforces limits under concurrent load.
 * SECURITY_AUDIT_PLAN Phase 1: "10 threads trying to exceed limit - verify only one succeeds"
 * (interpreted as: total cost never exceeds limit when many threads add cost concurrently)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestOAuth2ClientRepositoryConfig.class)
@DisplayName("Cost tracking concurrent security IT")
class CostTrackingConcurrentSecurityIT {

    private static final int THREAD_COUNT = 10;
    private static final BigDecimal COST_LIMIT = new BigDecimal("5.00");

    @Autowired
    private CostTrackingService costTrackingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ChatbotRepository chatbotRepository;

    private User previewUser;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        chatbotRepository.deleteAll();
        subscriptionRepository.deleteAll();
        userRepository.deleteAll();

        previewUser = new User();
        previewUser.setEmail("concurrent-preview@example.com");
        previewUser.setUsername("concurrentpreview");
        previewUser.setAuthProvider(User.AuthProvider.LOCAL);
        previewUser.setCurrentMonthCost(BigDecimal.ZERO);
        previewUser.setMonthlyCostLimit(COST_LIMIT);
        previewUser.setCostResetDate(LocalDateTime.now());
        previewUser = userRepository.save(previewUser);
    }

    @Test
    @DisplayName("Concurrent trackWebsiteScanCost never exceeds monthly limit")
    void concurrentTrackCostNeverExceedsLimit() throws InterruptedException {
        // Each call adds ~$0.30; 20 concurrent calls would be $6 if unbounded. Limit $5 → final <= 5.
        int pagesPerCall = 500;
        int tokensPerCall = 3_000_000;
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        Runnable task = () -> {
            try {
                start.await();
                costTrackingService.trackWebsiteScanCost(previewUser, pagesPerCall, tokensPerCall);
                completed.incrementAndGet();
            } catch (Exception e) {
                failures.incrementAndGet();
            }
        };

        for (int i = 0; i < THREAD_COUNT * 2; i++) {
            executor.submit(task);
        }
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);

        User after = userRepository.findById(previewUser.getId()).orElseThrow();
        BigDecimal finalCost = after.getCurrentMonthCost();

        assertThat(finalCost).isNotNull();
        assertThat(finalCost.compareTo(COST_LIMIT)).isLessThanOrEqualTo(0);
    }

}
