package com.prayer_chat.chatbot.config;

import com.prayer_chat.chatbot.service.EmbeddingImporterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * One-time runner to import embeddings from JSON file
 * 
 * Features:
 * - Automatically retries if file is not found immediately (waits up to 5 minutes by default)
 * - Gracefully handles missing files (doesn't fail service startup)
 * - Supports both relative and absolute file paths
 * 
 * Usage:
 * 1. Upload the embeddings file to the server (via Render Shell or other method)
 * 2. Set environment variable: IMPORT_EMBEDDINGS_FILE=data/bible_embeddings.json (or full path)
 * 3. Optionally configure retries:
 *    - IMPORT_EMBEDDINGS_MAX_RETRIES=10 (default: 10 attempts)
 *    - IMPORT_EMBEDDINGS_RETRY_DELAY_MS=30000 (default: 30 seconds between retries)
 * 4. Set profile: SPRING_PROFILES_ACTIVE=local,import-embeddings
 * 5. Restart service - import will run automatically (waits for file if needed)
 * 6. Remove environment variables after import completes
 * 
 * Note: Use relative path like "data/bible_embeddings.json" for persistence across restarts.
 * The runner will wait up to 5 minutes (10 retries × 30 seconds) for the file to appear.
 */
@Component
@Order(2) // Run after BibleDataInitializer (@Order(1))
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
        // Use System.out for maximum visibility
        System.out.println("=".repeat(60));
        System.out.println("✅ EmbeddingImportRunner CONSTRUCTOR CALLED - Component created!");
        System.out.println("✅ Profile: import-embeddings is active");
        System.out.println("=".repeat(60));
        logger.info("✅ EmbeddingImportRunner initialized (profile: import-embeddings)");
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("🔍 EmbeddingImportRunner.run() CALLED!");
        System.out.println("=".repeat(60));
        logger.info("🔍 EmbeddingImportRunner.run() called");
        String filePath = environment.getProperty("IMPORT_EMBEDDINGS_FILE");
        System.out.println("🔍 IMPORT_EMBEDDINGS_FILE value: " + (filePath != null ? filePath : "null"));
        logger.info("🔍 IMPORT_EMBEDDINGS_FILE value: {}", filePath != null ? filePath : "null");
        
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.info("IMPORT_EMBEDDINGS_FILE not set. Skipping embedding import.");
            logger.info("To import embeddings, set: IMPORT_EMBEDDINGS_FILE=data/bible_embeddings.json");
            return;
        }

        logger.info("=".repeat(60));
        logger.info("🚀 Starting automatic embedding import...");
        logger.info("📁 File path: {}", filePath);
        logger.info("=".repeat(60));

        // Resolve file path (handle relative paths)
        File file = resolveFilePath(filePath);
        
        // Retry mechanism: wait for file if it's not found immediately
        int maxRetries = Integer.parseInt(environment.getProperty("IMPORT_EMBEDDINGS_MAX_RETRIES", "10"));
        long retryDelayMs = Long.parseLong(environment.getProperty("IMPORT_EMBEDDINGS_RETRY_DELAY_MS", "30000")); // 30 seconds default
        
        File foundFile = waitForFile(file, maxRetries, retryDelayMs);
        
        if (foundFile == null) {
            logger.warn("=".repeat(60));
            logger.warn("⚠️  File not found after {} retries: {}", maxRetries, file.getAbsolutePath());
            logger.warn("⚠️  Please upload the file to this location:");
            logger.warn("⚠️    {}", file.getAbsolutePath());
            logger.warn("⚠️  Then manually restart the service to retry the import.");
            logger.warn("⚠️  The service will continue running normally.");
            logger.warn("=".repeat(60));
            return; // Exit gracefully, don't fail startup
        }

        logger.info("✅ File found: {} ({} bytes)", foundFile.getAbsolutePath(), foundFile.length());

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

    /**
     * Resolve file path for existence checking (logging only).
     * Actual import uses EmbeddingImporterService which has full security validation.
     */
    private File resolveFilePath(String filePath) {
        try {
            Path path = Paths.get(filePath).normalize();
            
            // Basic security: prevent path traversal
            String pathString = path.toString();
            if (pathString.contains("..")) {
                logger.warn("Path traversal detected in file path: {}", filePath);
                // Still return a file object for logging, but actual import will fail validation
            }
            
            // If absolute path, use as-is
            if (path.isAbsolute()) {
                return path.toFile();
            }
            
            // If relative path, resolve against working directory
            String workingDir = System.getProperty("user.dir");
            Path resolvedPath = Paths.get(workingDir, filePath).normalize();
            
            // Ensure it's within working directory (basic check)
            Path workingPath = Paths.get(workingDir);
            if (!resolvedPath.startsWith(workingPath)) {
                logger.warn("Resolved path escapes working directory: {}", filePath);
            }
            
            return resolvedPath.toFile();
        } catch (Exception e) {
            logger.warn("Error resolving file path: {}", filePath, e);
            return new File(filePath); // Fallback
        }
    }

    /**
     * Wait and retry checking for file existence.
     * Useful when file is uploaded after service startup.
     * 
     * @param file File to check for
     * @param maxRetries Maximum number of retry attempts
     * @param retryDelayMs Delay between retries in milliseconds
     * @return File if found, null if not found after all retries
     */
    private File waitForFile(File file, int maxRetries, long retryDelayMs) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            if (file.exists() && file.isFile() && file.length() > 0) {
                if (attempt > 1) {
                    logger.info("✅ File found on attempt {}/{}", attempt, maxRetries);
                }
                return file;
            }
            
            if (attempt < maxRetries) {
                logger.info("⏳ Waiting for file... (attempt {}/{}, retrying in {} seconds)", 
                    attempt, maxRetries, retryDelayMs / 1000);
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Retry interrupted");
                    return null;
                }
            }
        }
        
        return null; // File not found after all retries
    }
}

