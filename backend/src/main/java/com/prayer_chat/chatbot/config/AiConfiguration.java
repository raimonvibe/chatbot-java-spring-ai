package com.prayer_chat.chatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.AnthropicChatOptions;
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
    
    public AiConfiguration() {
        logger.debug("AiConfiguration initialized");
    }

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
        
        // Spring AI auto-config may not run (e.g. env available only after context starts). Create ChatModel manually.
        logger.info("Creating AnthropicChatModel from API key (Spring AI auto-config did not provide a bean).");
        
        try {
            AnthropicApi anthropicApi = AnthropicApi.builder().apiKey(apiKey).build();
            AnthropicChatOptions options = AnthropicChatOptions.builder()
                    .model("claude-3-haiku-20240307")
                    .temperature(0.7)
                    .maxTokens(1000)
                    .build();
            AnthropicChatModel anthropicChatModel = AnthropicChatModel.builder()
                    .anthropicApi(anthropicApi)
                    .defaultOptions(options)
                    .build();
            logger.info("AnthropicChatModel created successfully.");
            return anthropicChatModel;
        } catch (Exception e) {
            logger.error("Failed to create AnthropicChatModel manually", e);
            return (prompt) -> {
                throw new IllegalStateException(
                    "ChatModel configuration error. ANTHROPIC_API_KEY is set but failed to create AnthropicChatModel. Check spring-ai-anthropic dependency."
                );
            };
        }
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

    /**
     * Explicitly create EmbeddingImportRunner bean to ensure it's loaded
     * Always create it - let the runner itself check if IMPORT_EMBEDDINGS_FILE is set
     */
    @Bean
    public EmbeddingImportRunner embeddingImportRunner(
            com.prayer_chat.chatbot.service.EmbeddingImporterService embeddingImporterService,
            org.springframework.core.env.Environment environment,
            com.prayer_chat.chatbot.service.UrlValidationService urlValidationService) {
        return new EmbeddingImportRunner(embeddingImporterService, environment, urlValidationService);
    }
}
