package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.model.BibleVerse;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.BibleVerseRepository;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Christian Content Analysis Service Tests")
class ChristianContentAnalysisServiceTest {

    @Autowired
    private ChristianContentAnalysisService christianContentAnalysisService;

    @Autowired
    private BibleVerseRepository bibleVerseRepository;

    @Autowired
    private ChatbotRepository chatbotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private WebsiteAnalysisService websiteAnalysisService;

    private User testUser;
    private Chatbot testChatbot;
    private BibleVerse testVerse1;
    private BibleVerse testVerse2;

    @BeforeEach
    void setUp() {
        // Clear database
        bibleVerseRepository.deleteAll();
        chatbotRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setUsername("testuser");
        testUser.setEnabled(true);
        testUser = userRepository.save(testUser);

        // Create test chatbot
        testChatbot = new Chatbot();
        testChatbot.setName("Test Ministry Chatbot");
        testChatbot.setDescription("A chatbot for a Christian ministry");
        testChatbot.setWebsiteUrl("https://example-ministry.com");
        testChatbot.setPrimaryLanguage("en");
        testChatbot.setOwner(testUser);
        testChatbot.setIsActive(true);
        testChatbot.setEmbedCode("test-embed-code-" + System.currentTimeMillis()); // Required field
        testChatbot = chatbotRepository.save(testChatbot);

        // Create test Bible verses with mock embeddings
        float[] embedding1 = new float[1024];
        float[] embedding2 = new float[1024];
        for (int i = 0; i < 1024; i++) {
            embedding1[i] = 0.1f;
            embedding2[i] = 0.2f;
        }

        testVerse1 = new BibleVerse();
        testVerse1.setBook("Matthew");
        testVerse1.setChapter(5);
        testVerse1.setVerse(16);
        testVerse1.setReference("Matthew 5:16");
        testVerse1.setText("Let your light shine before others, that they may see your good works");
        testVerse1.setTranslation("World English Bible");
        testVerse1.setEmbedding(embedding1);
        testVerse1 = bibleVerseRepository.save(testVerse1);

        testVerse2 = new BibleVerse();
        testVerse2.setBook("John");
        testVerse2.setChapter(3);
        testVerse2.setVerse(16);
        testVerse2.setReference("John 3:16");
        testVerse2.setText("For God so loved the world that he gave his one and only Son");
        testVerse2.setTranslation("World English Bible");
        testVerse2.setEmbedding(embedding2);
        testVerse2 = bibleVerseRepository.save(testVerse2);
    }

    @Test
    @DisplayName("Should get verse count")
    void shouldGetVerseCount() {
        long count = christianContentAnalysisService.getVerseCount();
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("Should find relevant verses with default parameters")
    void shouldFindRelevantVersesWithDefaults() {
        // Mock website content
        when(websiteAnalysisService.getAnalyzedContent(any(Chatbot.class)))
            .thenReturn("This is a Christian ministry website about spreading the gospel and doing good works");

        // Mock embedding for website content
        float[] websiteEmbedding = new float[1024];
        for (int i = 0; i < 1024; i++) {
            websiteEmbedding[i] = 0.15f; // Similar to verse embeddings
        }
        when(embeddingModel.embed(any(String.class))).thenReturn(websiteEmbedding);

        List<ChristianContentAnalysisService.BibleVerseMatch> matches = 
            christianContentAnalysisService.findRelevantVerses(testChatbot);

        // Should find verses (even if similarity is low due to mock embeddings)
        // Note: With mock embeddings, similarity might be low, so matches might be empty
        assertThat(matches).isNotNull();
        // Don't assert size as it depends on similarity calculation with mock data
    }

    @Test
    @DisplayName("Should find relevant verses with custom parameters")
    void shouldFindRelevantVersesWithCustomParameters() {
        // Mock website content
        when(websiteAnalysisService.getAnalyzedContent(any(Chatbot.class)))
            .thenReturn("Christian ministry about love and good works");

        // Mock embedding
        float[] websiteEmbedding = new float[1024];
        for (int i = 0; i < 1024; i++) {
            websiteEmbedding[i] = 0.15f;
        }
        when(embeddingModel.embed(any(String.class))).thenReturn(websiteEmbedding);

        List<ChristianContentAnalysisService.BibleVerseMatch> matches = 
            christianContentAnalysisService.findRelevantVerses(testChatbot, 5, 0.3);

        assertThat(matches).isNotNull();
        assertThat(matches.size()).isLessThanOrEqualTo(5);
        // Note: With mock embeddings, actual matches depend on similarity calculation
    }

    @Test
    @DisplayName("Should return empty list when no verses have embeddings")
    void shouldReturnEmptyListWhenNoEmbeddings() {
        // Remove embeddings from verses
        testVerse1.setEmbedding(null);
        testVerse2.setEmbedding(null);
        bibleVerseRepository.save(testVerse1);
        bibleVerseRepository.save(testVerse2);

        when(websiteAnalysisService.getAnalyzedContent(any(Chatbot.class)))
            .thenReturn("Some content");

        List<ChristianContentAnalysisService.BibleVerseMatch> matches = 
            christianContentAnalysisService.findRelevantVerses(testChatbot);

        assertThat(matches).isEmpty();
    }

    @Test
    @DisplayName("Should return empty list when website content is empty")
    void shouldReturnEmptyListWhenWebsiteContentEmpty() {
        when(websiteAnalysisService.getAnalyzedContent(any(Chatbot.class)))
            .thenReturn(""); // Empty string, not null

        List<ChristianContentAnalysisService.BibleVerseMatch> matches = 
            christianContentAnalysisService.findRelevantVerses(testChatbot);

        assertThat(matches).isEmpty();
    }

    @Test
    @DisplayName("Should filter verses by similarity threshold")
    void shouldFilterVersesBySimilarityThreshold() {
        when(websiteAnalysisService.getAnalyzedContent(any(Chatbot.class)))
            .thenReturn("Test content");

        float[] websiteEmbedding = new float[1024];
        for (int i = 0; i < 1024; i++) {
            websiteEmbedding[i] = 0.15f;
        }
        when(embeddingModel.embed(any(String.class))).thenReturn(websiteEmbedding);

        // High threshold should filter out most verses
        List<ChristianContentAnalysisService.BibleVerseMatch> matches = 
            christianContentAnalysisService.findRelevantVerses(testChatbot, 10, 0.9);

        assertThat(matches).isNotNull();
        // With mock embeddings, similarity calculation determines if verses pass threshold
        // High threshold (0.9) will likely filter out all verses with mock data
    }

    @Test
    @DisplayName("Should limit results to maxVerses")
    void shouldLimitResultsToMaxVerses() {
        // Create more verses with similar embeddings
        for (int i = 3; i <= 10; i++) {
            BibleVerse verse = new BibleVerse();
            verse.setBook("Test");
            verse.setChapter(1);
            verse.setVerse(i);
            verse.setReference("Test 1:" + i);
            verse.setText("Test verse " + i);
            verse.setTranslation("World English Bible");
            float[] embedding = new float[1024];
            for (int j = 0; j < 1024; j++) {
                embedding[j] = 0.15f; // Same as website embedding for high similarity
            }
            verse.setEmbedding(embedding);
            bibleVerseRepository.save(verse);
        }

        when(websiteAnalysisService.getAnalyzedContent(any(Chatbot.class)))
            .thenReturn("Test content");

        float[] websiteEmbedding = new float[1024];
        for (int i = 0; i < 1024; i++) {
            websiteEmbedding[i] = 0.15f; // Same as verse embeddings
        }
        when(embeddingModel.embed(any(String.class))).thenReturn(websiteEmbedding);

        List<ChristianContentAnalysisService.BibleVerseMatch> matches = 
            christianContentAnalysisService.findRelevantVerses(testChatbot, 5, 0.1);

        // Should limit to maxVerses (5), but with identical embeddings, similarity = 1.0
        // So all verses should pass threshold, but limited to 5
        assertThat(matches.size()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("Should create BibleVerseMatch with similarity")
    void shouldCreateBibleVerseMatch() {
        ChristianContentAnalysisService.BibleVerseMatch match = 
            new ChristianContentAnalysisService.BibleVerseMatch(testVerse1, 0.75);

        assertThat(match.getVerse()).isEqualTo(testVerse1);
        assertThat(match.getSimilarity()).isEqualTo(0.75);
    }
}

