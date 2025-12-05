package com.tjanabot.chatbot.config;

import com.tjanabot.chatbot.config.CohereEmbeddingModel;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for AI services
 * Uses Claude (Anthropic) for chat and Cohere for embeddings
 * Only active in non-test profiles (tests use MockAiConfiguration)
 */
@Configuration
@Profile("!test")
public class AiConfiguration {

    @Value("${spring.ai.anthropic.api-key:${ANTHROPIC_API_KEY:}}")
    private String anthropicApiKey;

    @Value("${spring.ai.anthropic.base-url:https://api.anthropic.com}")
    private String anthropicBaseUrl;

    @Value("${spring.ai.anthropic.chat.options.model:claude-3-haiku-20240307}")
    private String anthropicModel;

    @Value("${spring.ai.anthropic.chat.options.temperature:0.7}")
    private Double anthropicTemperature;

    @Value("${spring.ai.anthropic.chat.options.max-tokens:1000}")
    private Integer anthropicMaxTokens;

    @Value("${spring.ai.cohere.api-key:${COHERE_API_KEY:}}")
    private String cohereApiKey;

    @Value("${app.embedding.model:embed-multilingual-v3.0}")
    private String embeddingModel;

    /**
     * Explicitly create AnthropicChatModel bean if auto-configuration doesn't work
     * This ensures the bean is available even if Spring AI auto-config fails
     */
    @Bean
    @ConditionalOnMissingBean(AnthropicChatModel.class)
    public AnthropicChatModel anthropicChatModel() {
        var anthropicApi = new AnthropicApi(anthropicBaseUrl, anthropicApiKey);
        var options = org.springframework.ai.anthropic.AnthropicChatOptions.builder()
                .withModel(anthropicModel)
                .withTemperature(anthropicTemperature)
                .withMaxTokens(anthropicMaxTokens)
                .build();
        return new AnthropicChatModel(anthropicApi, options);
    }

    /**
     * Primary ChatClient bean using Anthropic Claude
     */
    @Bean
    @Primary
    public ChatClient chatClient(AnthropicChatModel anthropicChatModel) {
        return ChatClient.builder(anthropicChatModel)
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
