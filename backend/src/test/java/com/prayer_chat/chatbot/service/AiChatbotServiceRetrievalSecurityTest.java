package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.WebsiteContent;
import com.prayer_chat.chatbot.repository.BibleVerseRepository;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.repository.ConversationRepository;
import com.prayer_chat.chatbot.repository.MessageRepository;
import com.prayer_chat.chatbot.repository.WebsiteContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Security and correctness for hybrid RAG retrieval: tenant isolation on vector hits,
 * DB snapshot scoped by {@link WebsiteContentRepository#findByChatbot(Chatbot)}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiChatbotService retrieval security tests")
class AiChatbotServiceRetrievalSecurityTest {

    @Mock
    private ChatClient chatClient;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private EmbeddingModel embeddingModel;
    @Mock
    private ChatbotRepository chatbotRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private WebsiteContentRepository websiteContentRepository;
    @Mock
    private BibleVerseRepository bibleVerseRepository;
    @Mock
    private WebhookService webhookService;
    @Mock
    private JesusTeachingsService jesusTeachingsService;

    private AiChatbotService aiChatbotService;

    @BeforeEach
    void setUp() {
        aiChatbotService = new AiChatbotService(
            chatClient, vectorStore, embeddingModel,
            chatbotRepository, conversationRepository, messageRepository,
            websiteContentRepository, bibleVerseRepository, webhookService, jesusTeachingsService
        );
    }

    @SuppressWarnings("unchecked")
    private List<Document> invokeRetrieve(Chatbot chatbot, String userMessage) throws Exception {
        Method m = AiChatbotService.class.getDeclaredMethod("retrieveRelevantContext", Chatbot.class, String.class);
        m.setAccessible(true);
        return (List<Document>) m.invoke(aiChatbotService, chatbot, userMessage);
    }

    @Test
    @DisplayName("SECURITY: vector candidates from other chatbots are dropped (post-filter by chatbotId)")
    void vectorHitsFilteredByChatbotId() throws Exception {
        Chatbot chatbot = new Chatbot();
        chatbot.setId(1L);
        chatbot.setName("Mine");

        when(websiteContentRepository.findByChatbot(chatbot)).thenReturn(List.of());

        Document otherTenant = new Document("leaked content", Map.of("chatbotId", "999", "url", "https://evil.example/"));
        Document mine = new Document("allowed", Map.of("chatbotId", "1", "url", "https://good.example/"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(otherTenant, mine));

        List<Document> result = invokeRetrieve(chatbot, "hello");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getText()).isEqualTo("allowed");
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    @DisplayName("SECURITY: documents without chatbotId metadata are not used as vector hits")
    void vectorHitsWithoutMetadataExcluded() throws Exception {
        Chatbot chatbot = new Chatbot();
        chatbot.setId(2L);

        when(websiteContentRepository.findByChatbot(chatbot)).thenReturn(List.of());

        Document noMeta = new Document("orphan", Map.of("url", "https://x.com/"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(noMeta));

        List<Document> result = invokeRetrieve(chatbot, "q");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Hybrid: DB pages for this chatbot are included when vector returns other-tenant only")
    void dbSnapshotStillLoadedForCorrectTenant() throws Exception {
        Chatbot chatbot = new Chatbot();
        chatbot.setId(5L);

        WebsiteContent row = new WebsiteContent(chatbot, "https://client.com/", "Home", "Welcome to our payment guide.");
        row.setId(10L);
        when(websiteContentRepository.findByChatbot(chatbot)).thenReturn(List.of(row));

        Document otherOnly = new Document("noise", Map.of("chatbotId", "88"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(otherOnly));

        List<Document> result = invokeRetrieve(chatbot, "what is this site");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getText()).contains("payment guide");
    }
}
