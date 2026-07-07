package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.WebsiteContent;
import com.prayer_chat.chatbot.repository.BibleVerseRepository;
import com.prayer_chat.chatbot.repository.WebsiteContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Security tests for hybrid RAG retrieval ({@link RagContextRetrievalService}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RAG context retrieval security tests")
class RagContextRetrievalServiceTest {

    @Mock
    private VectorStore vectorStore;
    @Mock
    private WebsiteContentRepository websiteContentRepository;
    @Mock
    private BibleVerseRepository bibleVerseRepository;
    @Mock
    private EmbeddingModel embeddingModel;

    private RagContextRetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        retrievalService = new RagContextRetrievalService(
            vectorStore, websiteContentRepository, bibleVerseRepository, embeddingModel);
        ReflectionTestUtils.setField(retrievalService, "ragObservabilityEnabled", false);
    }

    @Test
    @DisplayName("SECURITY: vector candidates from other chatbots are dropped (post-filter by chatbotId)")
    void vectorHitsFilteredByChatbotId() {
        Chatbot chatbot = new Chatbot();
        chatbot.setId(1L);
        chatbot.setName("Mine");

        when(websiteContentRepository.findByChatbot(chatbot)).thenReturn(List.of());

        Document otherTenant = new Document("leaked content", Map.of("chatbotId", "999", "url", "https://evil.example/"));
        Document mine = new Document("allowed", Map.of("chatbotId", "1", "url", "https://good.example/"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(otherTenant, mine));

        List<Document> result = retrievalService.retrieveRelevantContext(chatbot, "hello");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getText()).isEqualTo("allowed");
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    @DisplayName("SECURITY: documents without chatbotId metadata are not used as vector hits")
    void vectorHitsWithoutMetadataExcluded() {
        Chatbot chatbot = new Chatbot();
        chatbot.setId(2L);

        when(websiteContentRepository.findByChatbot(chatbot)).thenReturn(List.of());

        Document noMeta = new Document("orphan", Map.of("url", "https://x.com/"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(noMeta));

        List<Document> result = retrievalService.retrieveRelevantContext(chatbot, "q");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Hybrid: DB pages for this chatbot are included when vector returns other-tenant only")
    void dbSnapshotStillLoadedForCorrectTenant() {
        Chatbot chatbot = new Chatbot();
        chatbot.setId(5L);

        WebsiteContent row = new WebsiteContent(chatbot, "https://client.com/", "Home", "Welcome to our payment guide.");
        row.setId(10L);
        when(websiteContentRepository.findByChatbot(chatbot)).thenReturn(List.of(row));

        Document otherOnly = new Document("noise", Map.of("chatbotId", "88"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(otherOnly));

        List<Document> result = retrievalService.retrieveRelevantContext(chatbot, "what is this site");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getText()).contains("payment guide");
    }
}
