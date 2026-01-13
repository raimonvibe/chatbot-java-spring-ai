package com.prayer_chat.chatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.annotation.PostConstruct;

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

    // Debug: Log when configuration is being processed
    public AiConfiguration() {
        System.out.println("=".repeat(60));
        System.out.println("🔧 AiConfiguration constructor called");
        System.out.println("=".repeat(60));
        logger.info("AiConfiguration loaded - will create beans");
    }

    @Value("${spring.ai.anthropic.api-key:${ANTHROPIC_API_KEY:}}")
    private String anthropicApiKey;

    @Value("${spring.ai.cohere.api-key:${COHERE_API_KEY:}}")
    private String cohereApiKey;

    @Value("${app.embedding.model:embed-multilingual-v3.0}")
    private String embeddingModel;

    @PostConstruct
    public void logConfiguration() {
        logger.info("📋 AiConfiguration @PostConstruct called");
        logger.info("📋 Anthropic API Key present: {}", anthropicApiKey != null && !anthropicApiKey.isEmpty());
        logger.info("📋 Cohere API Key present: {}", cohereApiKey != null && !cohereApiKey.isEmpty());
        logger.info("📋 Embedding model: {}", embeddingModel);
        logger.info("📋 All @Bean methods should be invoked after this...");
    }

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
        
        // If we have an API key but Spring AI didn't auto-configure, manually create AnthropicChatModel
        logger.warn("ANTHROPIC_API_KEY is set (length: {}) but Spring AI auto-configuration didn't create ChatModel.", apiKey.length());
        logger.warn("Creating AnthropicChatModel manually as fallback.");
        logger.warn("This usually means Spring AI auto-configuration didn't detect the property.");
        logger.warn("Check that 'spring.ai.anthropic.api-key' property is set before Spring AI auto-configuration runs.");
        
        try {
            // Manually create AnthropicChatModel using the builder pattern
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
            logger.info("Successfully created AnthropicChatModel manually.");
            return anthropicChatModel;
        } catch (Exception e) {
            logger.error("Failed to create AnthropicChatModel manually: {}", e.getMessage(), e);
            return (prompt) -> {
                throw new IllegalStateException(
                    "ChatModel configuration error. " +
                    "ANTHROPIC_API_KEY is set but failed to create AnthropicChatModel. " +
                    "Error: " + e.getMessage() + ". " +
                    "Check that spring-ai-anthropic dependency is correct."
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
    @Primary
    public EmbeddingModel embeddingModel() {
        logger.info("🔧 Creating Cohere EmbeddingModel (model: {})", embeddingModel);
        logger.info("🔧 API Key configured: {}", cohereApiKey != null && !cohereApiKey.isEmpty() ? "Yes" : "No");
        return new CohereEmbeddingModel(cohereApiKey, embeddingModel);
    }

    /**
     * PostgreSQL VectorStore with pgvector extension
     * Provides persistent storage for embeddings with efficient similarity search
     *
     * This replaces SimpleVectorStore (in-memory) with a production-ready solution
     * that persists vectors in PostgreSQL and scales across multiple instances.
     *
     * @param jdbcTemplate Spring JDBC template for database operations
     * @param embeddingModel Cohere embedding model for generating embeddings
     * @return PgVectorStore instance configured for persistent vector storage
     */
    @Bean(name = "vectorStore")
    @Primary
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        logger.info("🔧 ====================================");
        logger.info("🔧 Creating MANUAL PgVectorStore bean");
        logger.info("🔧 Cohere embedding model: {}", embeddingModel);
        logger.info("🔧 JdbcTemplate: {}", jdbcTemplate != null ? "Available" : "NULL");
        logger.info("🔧 Initializing pgvector with 1024 dimensions");
        logger.info("🔧 ====================================");

        PgVectorStore vectorStore = new PgVectorStore.Builder(jdbcTemplate, embeddingModel)
                .initializeSchema(true)  // Auto-create vector_store table if not exists
                .build();

        logger.info("✅ PgVectorStore created successfully!");
        return vectorStore;
    }

    /**
     * ObjectMapper bean for JSON processing
     * Configured with JavaTimeModule for LocalDateTime serialization support
     * 
     * This is needed because:
     * 1. We use Jackson 2.x (Spring Boot 4.0 defaults to Jackson 3.x)
     * 2. LocalDateTime fields need JavaTimeModule to serialize properly
     * 3. Without this, GET /api/chatbots will fail with serialization errors
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Register JSR310 module for Java 8 Date/Time API (LocalDateTime, etc.)
        mapper.registerModule(new JavaTimeModule());
        
        // Disable timestamps (use ISO-8601 strings instead)
        // This makes dates readable: "2025-12-23T18:38:08" instead of [2025, 12, 23, 18, 38, 8]
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        return mapper;
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
        System.out.println("=".repeat(60));
        System.out.println("🔧 @Bean method embeddingImportRunner() CALLED!");
        System.out.println("🔧 Creating EmbeddingImportRunner bean (will check env var in run method)");
        
        // Debug: Check environment variable
        String envVar = System.getenv("IMPORT_EMBEDDINGS_FILE");
        String prop = environment.getProperty("IMPORT_EMBEDDINGS_FILE");
        System.out.println("🔍 System.getenv('IMPORT_EMBEDDINGS_FILE'): " + (envVar != null ? envVar : "null"));
        System.out.println("🔍 environment.getProperty('IMPORT_EMBEDDINGS_FILE'): " + (prop != null ? prop : "null"));
        System.out.println("=".repeat(60));
        
        logger.info("Creating EmbeddingImportRunner bean");
        try {
            EmbeddingImportRunner runner = new EmbeddingImportRunner(embeddingImporterService, environment, urlValidationService);
            System.out.println("✅ EmbeddingImportRunner bean created successfully");
            return runner;
        } catch (Exception e) {
            System.out.println("❌ Error creating EmbeddingImportRunner: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
