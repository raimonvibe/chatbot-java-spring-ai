package com.tjanabot.chatbot.config;

import com.tjanabot.chatbot.config.CohereEmbeddingModel;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

/**
 * Configuration for AI services
 * Uses Claude (Anthropic) for chat and Cohere for embeddings
 * Only active in non-test profiles (tests use MockAiConfiguration)
 *
 * Spring AI Configuration Strategy:
 * 1. Tries Spring Boot auto-configuration first (if available)
 * 2. Falls back to manual bean creation if auto-config doesn't work
 * 3. Uses environment variables for API keys
 */
@Configuration
@Profile("!test")
public class AiConfiguration {

    @Value("${spring.ai.anthropic.api-key:${ANTHROPIC_API_KEY}}")
    private String anthropicApiKey;

    @Value("${spring.ai.cohere.api-key:${COHERE_API_KEY:}}")
    private String cohereApiKey;

    @Value("${app.embedding.model:embed-multilingual-v3.0}")
    private String embeddingModel;

    /**
     * Fallback ChatModel bean if Spring AI auto-configuration doesn't work
     * Uses AnthropicChatModel.Builder pattern for Spring AI 1.0.0+
     */
    @Bean
    @ConditionalOnMissingBean(ChatModel.class)
    @ConditionalOnProperty(name = "spring.ai.anthropic.api-key")
    public ChatModel anthropicChatModel(RestClient.Builder restClientBuilder) {
        return AnthropicChatModel.builder()
                .apiKey(anthropicApiKey)
                .restClientBuilder(restClientBuilder)
                .build();
    }

    /**
     * Primary ChatClient bean using Anthropic Claude
     *
     * ChatModel is either:
     * - Auto-configured by Spring AI (preferred), OR
     * - Created by anthropicChatModel() fallback bean above
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
