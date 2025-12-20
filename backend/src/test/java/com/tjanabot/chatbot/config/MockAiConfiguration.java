package com.tjanabot.chatbot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test configuration that provides mocked AI components for testing.
 * This allows tests to run without real AI service connections while still
 * loading the full application context with all controllers and services.
 */
@Configuration
@Profile("test")
public class MockAiConfiguration {

    /**
     * Provides a mocked ChatClient for tests.
     * Returns empty responses for all chat prompts.
     */
    @Bean
    @Primary
    public ChatClient mockChatClient() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);

        // Mock the builder pattern
        when(builder.build()).thenReturn(chatClient);

        return chatClient;
    }

    /**
     * Provides a mocked ChatClient.Builder for tests.
     */
    @Bean
    @Primary
    public ChatClient.Builder mockChatClientBuilder() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mockChatClient();

        when(builder.build()).thenReturn(chatClient);
        when(builder.defaultSystem(any(String.class))).thenReturn(builder);

        return builder;
    }

    /**
     * Provides a mocked EmbeddingModel for tests.
     * Returns empty embedding responses.
     */
    @Bean
    @Primary
    public EmbeddingModel mockEmbeddingModel() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        // Mock embedding responses
        EmbeddingResponse mockResponse = mock(EmbeddingResponse.class);
        when(mockResponse.getResults()).thenReturn(Collections.emptyList());
        when(embeddingModel.embedForResponse(anyList())).thenReturn(mockResponse);

        return embeddingModel;
    }

    /**
     * Provides a mocked VectorStore for tests.
     * Returns empty search results.
     */
    @Bean
    @Primary
    public VectorStore mockVectorStore() {
        VectorStore vectorStore = mock(VectorStore.class);

        // Mock vector store operations
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
            .thenReturn(Collections.emptyList());
        when(vectorStore.similaritySearch(any(String.class)))
            .thenReturn(Collections.emptyList());

        return vectorStore;
    }
}
