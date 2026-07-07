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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Security tests for AiChatbotService batch indexing (indexWebsiteContent).
 * Ensures tenant isolation, fixed batch size (no injection), null-safety, and batch cap.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiChatbotService indexing security tests")
class AiChatbotServiceIndexingSecurityTest {

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
    @Mock
    private RagContextRetrievalService ragContextRetrievalService;
    @Mock
    private RagPromptBuilder ragPromptBuilder;

    private AiChatbotService aiChatbotService;

    @BeforeEach
    void setUp() {
        aiChatbotService = new AiChatbotService(
            chatClient, vectorStore, embeddingModel,
            chatbotRepository, conversationRepository, messageRepository,
            websiteContentRepository, bibleVerseRepository, webhookService, jesusTeachingsService,
            ragContextRetrievalService, ragPromptBuilder
        );
    }

    @Test
    @DisplayName("SECURITY: indexWebsiteContent uses only the provided chatbot (tenant isolation)")
    void indexWebsiteContent_usesOnlyProvidedChatbot() {
        Chatbot chatbot = new Chatbot();
        chatbot.setId(1L);
        chatbot.setName("Bot 1");
        chatbot.setWebsiteUrl("https://example.com");

        WebsiteContent content = new WebsiteContent(chatbot, "https://example.com/page", "Title", "Body text");
        content.setId(100L);

        when(websiteContentRepository.findByChatbot(eq(chatbot), any()))
            .thenReturn(new PageImpl<>(List.of(content), PageRequest.of(0, 10), 1));

        aiChatbotService.indexWebsiteContent(chatbot);

        ArgumentCaptor<Chatbot> chatbotCaptor = ArgumentCaptor.forClass(Chatbot.class);
        ArgumentCaptor<org.springframework.data.domain.Pageable> pageableCaptor =
            ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(websiteContentRepository, atLeastOnce()).findByChatbot(chatbotCaptor.capture(), pageableCaptor.capture());

        assertThat(chatbotCaptor.getAllValues()).allMatch(c -> c.getId() == 1L);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("SECURITY: batch size is fixed at 10 (no user-controlled page size)")
    void indexWebsiteContent_usesFixedBatchSize() {
        Chatbot chatbot = new Chatbot();
        chatbot.setId(2L);
        chatbot.setName("Bot");
        chatbot.setWebsiteUrl("https://site.com");

        when(websiteContentRepository.findByChatbot(eq(chatbot), any()))
            .thenReturn(new PageImpl<>(Collections.emptyList()));

        aiChatbotService.indexWebsiteContent(chatbot);

        ArgumentCaptor<org.springframework.data.domain.Pageable> pageableCaptor =
            ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(websiteContentRepository).findByChatbot(eq(chatbot), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("SECURITY: documents added to vector store have chatbotId from parameter (not from content)")
    @SuppressWarnings("unchecked")
    void indexWebsiteContent_documentMetadataUsesParameterChatbotId() {
        Chatbot chatbot = new Chatbot();
        chatbot.setId(42L);
        chatbot.setName("My Bot");
        chatbot.setWebsiteUrl("https://example.com");

        WebsiteContent content = new WebsiteContent(chatbot, "https://example.com/", "Home", "Content");
        when(websiteContentRepository.findByChatbot(eq(chatbot), any()))
            .thenReturn(new PageImpl<>(List.of(content), PageRequest.of(0, 10), 1));

        aiChatbotService.indexWebsiteContent(chatbot);

        ArgumentCaptor<List<Document>> docsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(docsCaptor.capture());
        List<Document> docs = docsCaptor.getValue();
        assertThat(docs).hasSize(1);
        Map<String, Object> metadata = docs.get(0).getMetadata();
        assertThat(metadata).containsEntry("chatbotId", "42");
    }

    @Test
    @DisplayName("SECURITY: null chatbot does not call repository or vector store")
    void indexWebsiteContent_nullChatbot_skipsIndexing() {
        aiChatbotService.indexWebsiteContent(null);

        verify(websiteContentRepository, never()).findByChatbot(any(), any());
        verify(vectorStore, never()).add(any());
    }

    @Test
    @DisplayName("SECURITY: chatbot with null id does not call repository or vector store")
    void indexWebsiteContent_nullChatbotId_skipsIndexing() {
        Chatbot chatbot = new Chatbot();
        chatbot.setId(null);
        chatbot.setName("No Id");
        chatbot.setWebsiteUrl("https://example.com");

        aiChatbotService.indexWebsiteContent(chatbot);

        verify(websiteContentRepository, never()).findByChatbot(any(), any());
        verify(vectorStore, never()).add(any());
    }

    @Test
    @DisplayName("SECURITY: batch loop is bounded (stops after empty page)")
    void indexWebsiteContent_stopsOnEmptyPage() {
        Chatbot chatbot = new Chatbot();
        chatbot.setId(3L);
        chatbot.setName("Bot");
        chatbot.setWebsiteUrl("https://example.com");

        when(websiteContentRepository.findByChatbot(eq(chatbot), any()))
            .thenReturn(new PageImpl<>(Collections.emptyList()));

        aiChatbotService.indexWebsiteContent(chatbot);

        verify(websiteContentRepository, times(1)).findByChatbot(any(), any());
    }

    @Test
    @DisplayName("SECURITY: batch loop is capped at max batches (DoS prevention)")
    void indexWebsiteContent_respectsMaxBatchCap() {
        Chatbot chatbot = new Chatbot();
        chatbot.setId(4L);
        chatbot.setName("Bot");
        chatbot.setWebsiteUrl("https://example.com");

        WebsiteContent content = new WebsiteContent(chatbot, "https://example.com/", "Title", "Content");
        // Always return a non-empty page with hasNext=true so without cap we would loop forever
        when(websiteContentRepository.findByChatbot(eq(chatbot), any()))
            .thenReturn(new PageImpl<>(List.of(content), PageRequest.of(0, 10), 10_000));

        aiChatbotService.indexWebsiteContent(chatbot);

        // Must stop at 500 batches (INDEXING_MAX_BATCHES), not run unbounded
        verify(websiteContentRepository, atMost(500)).findByChatbot(any(), any());
        verify(websiteContentRepository, atLeast(1)).findByChatbot(any(), any());
    }
}
