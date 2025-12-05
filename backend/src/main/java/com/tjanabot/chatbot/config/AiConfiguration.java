package com.tjanabot.chatbot.config;

import com.tjanabot.chatbot.config.CohereEmbeddingModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Spring AI autoconfiguration is handled by Spring Boot's main @SpringBootApplication
 * This configuration just creates the ChatClient bean using the auto-configured ChatModel
 *
 * Required environment variables:
 * - ANTHROPIC_API_KEY: Anthropic API key for Claude
 * - COHERE_API_KEY: Cohere API key for embeddings
 */
@Configuration
@Profile("!test")
public class AiConfiguration {

    @Value("${spring.ai.cohere.api-key:${COHERE_API_KEY:}}")
    private String cohereApiKey;

    @Value("${app.embedding.model:embed-multilingual-v3.0}")
    private String embeddingModel;

    /**
     * ChatModel bean - will be auto-configured by Spring AI if ANTHROPIC_API_KEY is set
     * If not auto-configured, creates a stub that throws helpful error when used
     */
    @Bean
    @Primary
    public ChatModel chatModel() {
        // This creates a stub that will fail with a helpful message
        // Spring AI should override this with auto-configured AnthropicChatModel if API key is set
        return (prompt) -> {
            throw new IllegalStateException(
                "ChatModel not properly configured! " +
                "Ensure ANTHROPIC_API_KEY environment variable is set. " +
                "Check Render dashboard environment variables."
            );
        };
    }

    /**
     * Primary ChatClient bean using Anthropic Claude
     * Uses the ChatModel bean (either auto-configured or stub above)
     */
    @Bean
    @Primary
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
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
