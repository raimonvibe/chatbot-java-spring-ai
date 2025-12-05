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
 * - AnthropicChatModel is auto-configured by Spring Boot when:
 *   1. spring-ai-anthropic dependency is present (in pom.xml)
 *   2. spring.ai.anthropic.api-key property is set
 * - Configuration is in application.yml under spring.ai.anthropic.*
 * - If auto-configuration fails, check that ANTHROPIC_API_KEY env var is set
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
     * IMPORTANT: ChatModel is auto-configured by Spring AI
     * - Requires: spring-ai-anthropic dependency (in pom.xml)
     * - Requires: spring.ai.anthropic.api-key property
     * - Set via: ANTHROPIC_API_KEY environment variable
     *
     * If this fails with "No qualifying bean of type ChatModel":
     * 1. Verify ANTHROPIC_API_KEY is set in environment
     * 2. Check application.yml has spring.ai.anthropic.api-key: ${ANTHROPIC_API_KEY}
     * 3. Ensure spring-ai-anthropic is in pom.xml dependencies
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
