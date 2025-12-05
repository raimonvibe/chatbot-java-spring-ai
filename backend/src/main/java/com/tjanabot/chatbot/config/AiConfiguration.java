package com.tjanabot.chatbot.config;

import com.tjanabot.chatbot.config.CohereEmbeddingModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for AI services
 * Uses Claude (Anthropic) for chat and Cohere for embeddings
 * Only active in non-test profiles (tests use MockAiConfiguration)
 *
 * Spring AI Auto-Configuration:
 * - AnthropicChatModel is auto-configured by Spring Boot using application.yml properties
 * - Configuration properties: spring.ai.anthropic.api-key, spring.ai.anthropic.chat.options.*
 * - See: org.springframework.ai.autoconfigure.anthropic.AnthropicAutoConfiguration
 */
@Configuration
@Profile("!test")
public class AiConfiguration {

    @Value("${spring.ai.cohere.api-key:${COHERE_API_KEY:}}")
    private String cohereApiKey;

    @Value("${app.embedding.model:embed-multilingual-v3.0}")
    private String embeddingModel;

    /**
     * Primary ChatClient bean using Anthropic Claude
     *
     * ChatModel is auto-configured by Spring AI based on application.yml:
     * - spring.ai.anthropic.api-key
     * - spring.ai.anthropic.chat.options.model
     * - spring.ai.anthropic.chat.options.temperature
     * - spring.ai.anthropic.chat.options.max-tokens
     */
    @Bean
    @Primary
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .build();
    }

    /**
     * Cohere EmbeddingModel bean
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new CohereEmbeddingModel(cohereApiKey, embeddingModel);
    }

    /**
     * Simple in-memory VectorStore for development
     * For production, configure Pinecone or another persistent vector store
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
