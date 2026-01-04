package com.prayer_chat.chatbot.config;

import com.prayer_chat.chatbot.service.EmbeddingImporterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * One-time runner to import embeddings from JSON file
 * 
 * Usage:
 * 1. Set environment variable: IMPORT_EMBEDDINGS_FILE=/tmp/data/bible_embeddings.json
 * 2. Set profile: SPRING_PROFILES_ACTIVE=local,import-embeddings
 * 3. Restart service - import will run automatically
 * 4. Remove environment variables after import completes
 */
@Component
@Profile("import-embeddings")
public class EmbeddingImportRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingImportRunner.class);

    private final EmbeddingImporterService embeddingImporterService;
    private final Environment environment;

    public EmbeddingImportRunner(
            EmbeddingImporterService embeddingImporterService,
            Environment environment) {
        this.embeddingImporterService = embeddingImporterService;
        this.environment = environment;
    }

    @Override
    public void run(String... args) throws Exception {
        String filePath = environment.getProperty("IMPORT_EMBEDDINGS_FILE");
        
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.info("IMPORT_EMBEDDINGS_FILE not set. Skipping embedding import.");
            logger.info("To import embeddings, set: IMPORT_EMBEDDINGS_FILE=/tmp/data/bible_embeddings.json");
            return;
        }

        logger.info("=".repeat(60));
        logger.info("🚀 Starting automatic embedding import...");
        logger.info("📁 File path: {}", filePath);
        logger.info("=".repeat(60));

        try {
            int imported = embeddingImporterService.importEmbeddings(filePath);
            
            logger.info("=".repeat(60));
            logger.info("✅ Embedding import completed successfully!");
            logger.info("📊 Imported embeddings for {} verses", imported);
            logger.info("=".repeat(60));
            logger.info("⚠️  IMPORTANT: Remove IMPORT_EMBEDDINGS_FILE environment variable");
            logger.info("⚠️  IMPORTANT: Remove 'import-embeddings' from SPRING_PROFILES_ACTIVE");
            logger.info("=".repeat(60));
            
        } catch (Exception e) {
            logger.error("❌ Error importing embeddings", e);
            logger.error("Please check the file path and try again.");
            throw e;
        }
    }
}

