package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.helpers.TestDataBuilder;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.model.WebsiteContent;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Local debug test for website crawl: run against a real URL to see why content might not load.
 * <p>
 * <b>No token/API cost:</b> This test only runs the crawl (Jsoup fetch + extract + DB save).
 * It does NOT call {@code indexWebsiteContent()} or the embedding API, so no Cohere or other
 * API tokens are used when you run this test.
 * <p>
 * Disabled by default so CI does not hit the network. To run locally:
 * <ul>
 *   <li>Remove or comment out {@code @Disabled} below</li>
 *   <li>Run: {@code mvn test -Dtest=WebsiteAnalysisCrawlDebugIT}</li>
 * </ul>
 * Use this to verify that https://nigerian-tech-opportunities.vercel.app/ (or any URL)
 * returns at least one page of content so the chatbot can answer "about this site".
 */
@SpringBootTest
@ActiveProfiles("test")
@Disabled("Enable for local debugging: remove @Disabled and run mvn test -Dtest=WebsiteAnalysisCrawlDebugIT")
@DisplayName("Website analysis crawl – debug (real URL)")
class WebsiteAnalysisCrawlDebugIT {

    private static final String TEST_URL = "https://nigerian-tech-opportunities.vercel.app/";
    private static final long CRAWL_TIMEOUT_SECONDS = 90;

    @Autowired
    private WebsiteAnalysisService websiteAnalysisService;

    @Autowired
    private ChatbotRepository chatbotRepository;

    @Autowired
    private UserRepository userRepository;

    private Chatbot savedChatbot;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        chatbotRepository.deleteAll();

        User owner = TestDataBuilder.createTestUser("crawl-debug@example.com");
        owner = userRepository.save(owner);

        Chatbot chatbot = new Chatbot();
        chatbot.setName("Nigerian Tech Opportunities");
        chatbot.setWebsiteUrl(TEST_URL);
        chatbot.setOwner(owner);
        savedChatbot = chatbotRepository.save(chatbot);
    }

    @Test
    @DisplayName("Crawl test URL and assert at least one page is extracted")
    void crawlNigerianTechOpportunities_andExtractAtLeastOnePage() throws Exception {
        CompletableFuture<List<WebsiteContent>> future = websiteAnalysisService.analyzeWebsite(savedChatbot);
        List<WebsiteContent> contents = future.get(CRAWL_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Log for debugging
        System.out.println("Crawl completed. Pages extracted: " + (contents != null ? contents.size() : 0));
        if (contents != null && !contents.isEmpty()) {
            for (int i = 0; i < contents.size(); i++) {
                WebsiteContent c = contents.get(i);
                System.out.println("  Page " + (i + 1) + ": title=" + c.getTitle()
                    + ", contentLength=" + (c.getContent() != null ? c.getContent().length() : 0)
                    + ", wordCount=" + c.getWordCount());
            }
        }

        assertThat(contents)
            .as("Crawl should extract at least one page so chatbot can answer about the site")
            .isNotNull()
            .isNotEmpty();
    }
}
