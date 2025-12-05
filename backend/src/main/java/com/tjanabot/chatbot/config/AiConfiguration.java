package com.tjanabot.chatbot.config;

import com.tjanabot.chatbot.config.CohereEmbeddingModel;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
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
     * AnthropicApi bean - required by AnthropicChatModel
     */
    @Bean
    public AnthropicApi anthropicApi() {
        return new AnthropicApi(anthropicApiKey);
    }

    /**
     * ChatModel bean using Anthropic Claude
     * Created manually since Spring AI auto-configuration doesn't always work
     */
    @Bean
    public ChatModel anthropicChatModel(AnthropicApi anthropicApi) {
        var options = AnthropicChatOptions.builder()
                .model(AnthropicApi.ChatModel.CLAUDE_3_HAIKU.getValue())
                .temperature(0.7)
                .maxTokens(1000)
                .build();

        return new AnthropicChatModel(anthropicApi, options);
    }

    /**
     * Primary ChatClient bean using Anthropic Claude
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
