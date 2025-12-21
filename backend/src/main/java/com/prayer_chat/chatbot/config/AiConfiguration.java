package com.prayer_chat.chatbot.config;

import com.prayer_chat.chatbot.config.CohereEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
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

    private static final Logger logger = LoggerFactory.getLogger(AiConfiguration.class);

    @Value("${spring.ai.anthropic.api-key:${ANTHROPIC_API_KEY:}}")
    private String anthropicApiKey;

    @Value("${spring.ai.cohere.api-key:${COHERE_API_KEY:}}")
    private String cohereApiKey;

    @Value("${app.embedding.model:embed-multilingual-v3.0}")
    private String embeddingModel;

    /**
     * ChatModel bean - Spring AI will auto-configure this if spring.ai.anthropic.api-key is set.
     * 
     * If auto-configuration fails, this fallback manually creates the AnthropicChatModel.
     */
    @Bean
    @ConditionalOnMissingBean(ChatModel.class)
    public ChatModel chatModel() {
        // This fallback is only used if Spring AI auto-configuration fails
        // Check if API key is available
        String apiKey = anthropicApiKey != null && !anthropicApiKey.trim().isEmpty() 
            ? anthropicApiKey 
            : System.getenv("ANTHROPIC_API_KEY");
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.error("ANTHROPIC_API_KEY is not set! ChatModel will not work.");
            logger.error("Please set ANTHROPIC_API_KEY in your .env file or as environment variable.");
            return (prompt) -> {
                throw new IllegalStateException(
                    "ChatModel not properly configured! " +
                    "Ensure ANTHROPIC_API_KEY environment variable is set. " +
                    "For local development, add it to your .env file. " +
                    "For production (Render), set it in the Render dashboard environment variables."
                );
            };
        }
        
        // If we have an API key but Spring AI didn't auto-configure, provide helpful error
        logger.error("ANTHROPIC_API_KEY is set (length: {}) but Spring AI auto-configuration didn't create ChatModel.", apiKey.length());
        logger.error("This usually means Spring AI auto-configuration didn't detect the property.");
        logger.error("Check that 'spring.ai.anthropic.api-key' property is set before Spring AI auto-configuration runs.");
        logger.error("EnvironmentVariableConfig should have set this property. Check startup logs for confirmation.");
        
        return (prompt) -> {
            throw new IllegalStateException(
                "ChatModel configuration error. " +
                "ANTHROPIC_API_KEY is set but Spring AI auto-configuration failed to create AnthropicChatModel. " +
                "The API key was found (length: " + apiKey.length() + ") but Spring AI didn't auto-configure. " +
                "This may be a Spring AI version compatibility issue. " +
                "Check that spring-ai-anthropic dependency is correct and Spring AI auto-configuration is enabled."
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
