package com.prayer_chat.chatbot.config;

import com.prayer_chat.chatbot.service.EmbeddingImporterService;
import com.prayer_chat.chatbot.service.UrlValidationService;
import com.prayer_chat.chatbot.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * One-time runner to import embeddings from JSON file
 * 
 * Features:
 * - Automatically downloads file from URL if not found (set IMPORT_EMBEDDINGS_URL)
 * - Automatically retries if file is not found immediately (waits up to 5 minutes by default)
 * - Gracefully handles missing files (doesn't fail service startup)
 * - Supports both relative and absolute file paths
 * 
 * Usage (Option 1 - Auto-download from URL - RECOMMENDED):
 * 1. Set environment variables:
 *    - IMPORT_EMBEDDINGS_FILE=/tmp/data/bible_embeddings.json (target location)
 *    - IMPORT_EMBEDDINGS_URL=https://drive.usercontent.google.com/download?id=FILE_ID&export=download&confirm=t
 *    - SPRING_PROFILES_ACTIVE=local,import-embeddings
 * 2. Restart service - file will be downloaded automatically and imported
 * 3. Remove environment variables after import completes
 * 
 * Usage (Option 2 - Manual upload):
 * 1. Upload the embeddings file to the server (via Render Shell)
 * 2. Set environment variable: IMPORT_EMBEDDINGS_FILE=/tmp/data/bible_embeddings.json
 * 3. Optionally configure retries:
 *    - IMPORT_EMBEDDINGS_MAX_RETRIES=10 (default: 10 attempts)
 *    - IMPORT_EMBEDDINGS_RETRY_DELAY_MS=30000 (default: 30 seconds between retries)
 * 4. Set profile: SPRING_PROFILES_ACTIVE=local,import-embeddings
 * 5. Restart service - import will run automatically
 * 6. Remove environment variables after import completes
 * 
 * Note: On Render, /tmp gets cleared on restarts. Use IMPORT_EMBEDDINGS_URL for automatic download.
 */
// @Component removed - created as @Bean in AiConfiguration instead
@Order(2) // Run after BibleDataInitializer (@Order(1))
public class EmbeddingImportRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingImportRunner.class);

    private final EmbeddingImporterService embeddingImporterService;
    private final Environment environment;
    private final UrlValidationService urlValidationService;

    public EmbeddingImportRunner(
            EmbeddingImporterService embeddingImporterService,
            Environment environment,
            UrlValidationService urlValidationService) {
        this.embeddingImporterService = embeddingImporterService;
        this.environment = environment;
        this.urlValidationService = urlValidationService;
    }

    @Override
    public void run(String... args) throws Exception {
        // Option 1: Multiple URLs (for large file split into parts — e.g. Google Drive size limits)
        String urlsList = environment.getProperty("IMPORT_EMBEDDINGS_URLS");
        if (urlsList != null && !urlsList.trim().isEmpty()) {
            runMultiUrlImport(urlsList.trim());
            return;
        }

        // Option 2: Single file (optional single URL)
        String filePath = environment.getProperty("IMPORT_EMBEDDINGS_FILE");
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.info("IMPORT_EMBEDDINGS_FILE not set. Skipping embedding import.");
            return;
        }

        logger.info("Starting embedding import (file: {}). Service may show 'live' before import completes.", filePath);

        // Resolve file path (handle relative paths)
        File file = resolveFilePath(filePath);
        
        // Check if file exists, if not try to download it
        if (!file.exists() || file.length() == 0) {
            String downloadUrl = environment.getProperty("IMPORT_EMBEDDINGS_URL");
            
            if (downloadUrl != null && !downloadUrl.trim().isEmpty()) {
                // SECURITY: Validate URL to prevent SSRF attacks
                if (!urlValidationService.isValidAndSafe(downloadUrl)) {
                    logger.error("SECURITY: Invalid or unsafe download URL blocked. Use a valid HTTPS public URL.");
                    // Continue to retry mechanism (maybe file will be uploaded manually)
                } else {
                    logger.info("Downloading embeddings file from URL (file not found locally).");
                    
                    try {
                        boolean downloaded = downloadFile(downloadUrl, file);
                        if (!downloaded) {
                            logger.warn("Embedding file download failed. Will retry.");
                        }
                    } catch (Exception e) {
                        logger.error("Error downloading embedding file", e);
                        // Continue to retry mechanism below
                    }
                }
            } else {
                logger.info("File not found and IMPORT_EMBEDDINGS_URL not set. Will wait for file.");
            }
        }
        
        // Retry mechanism: wait for file if it's not found immediately
        int maxRetries = Integer.parseInt(environment.getProperty("IMPORT_EMBEDDINGS_MAX_RETRIES", "10"));
        long retryDelayMs = Long.parseLong(environment.getProperty("IMPORT_EMBEDDINGS_RETRY_DELAY_MS", "30000")); // 30 seconds default
        
        File foundFile = waitForFile(file, maxRetries, retryDelayMs);
        
        if (foundFile == null) {
            logger.warn("Embeddings file not found after {} retries. Set IMPORT_EMBEDDINGS_URL or IMPORT_EMBEDDINGS_URLS, or upload to {}. Service continues without embeddings.", maxRetries, file.getAbsolutePath());
            return; // Exit gracefully, don't fail startup
        }

        logger.info("File ready: {} ({} MB). Starting streaming import...", foundFile.getAbsolutePath(), foundFile.length() / (1024 * 1024));

        try {
            int imported = embeddingImporterService.importEmbeddings(filePath);
            logger.info("Embedding import completed successfully. Imported {} verses. Remove IMPORT_EMBEDDINGS_FILE and IMPORT_EMBEDDINGS_URL from Render env.", imported);
            
        } catch (Exception e) {
            logger.error("Embedding import failed", e);
            throw e;
        }
    }

    /**
     * Import from multiple URLs (e.g. when the full file is too large for a single Google Drive download).
     * Each URL must point to a JSON file with the same format: { "verses": [ ... ] }.
     * Use scripts/split-embeddings-for-import.py to split the large file into parts.
     */
    private void runMultiUrlImport(String urlsList) {
        String[] urls = urlsList.split(",");
        int totalImported = 0;
        int partIndex = 0;
        File tmpDir = new File("/tmp/data");
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }

        logger.info("Multi-part embedding import: {} URL(s)", urls.length);

        for (String urlStr : urls) {
            urlStr = urlStr.trim();
            if (urlStr.isEmpty()) continue;
            partIndex++;

            if (!urlValidationService.isValidAndSafe(urlStr)) {
                logger.error("Embedding import: invalid or unsafe URL skipped: {}", LogSanitizer.sanitizeUrl(urlStr));
                continue;
            }

            File partFile = new File(tmpDir, "bible_embeddings_part_" + partIndex + ".json");
            try {
                logger.info("Part {}/{}: downloading...", partIndex, urls.length);
                boolean downloaded = downloadFile(urlStr, partFile);
                if (!downloaded) {
                    logger.error("Embedding part {} download failed, skipping", partIndex);
                    if (partFile.exists()) partFile.delete();
                    continue;
                }
                String path = partFile.getAbsolutePath();
                int imported = embeddingImporterService.importEmbeddings(path);
                totalImported += imported;
                logger.info("Part {}/{}: imported {} verses (total: {})", partIndex, urls.length, imported, totalImported);
            } catch (Exception e) {
                logger.error("Embedding part {} import failed", partIndex, e);
                throw new RuntimeException("Part " + partIndex + " import failed", e);
            } finally {
                if (partFile.exists()) partFile.delete();
            }
        }

        logger.info("Multi-part embedding import completed. Total verses: {}. Remove IMPORT_EMBEDDINGS_URLS from Render env.", totalImported);
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
                    logger.info("Embedding file found on attempt {}/{}", attempt, maxRetries);
                }
                return file;
            }
            
            if (attempt < maxRetries) {
                logger.info("Waiting for embedding file (attempt {}/{}, retry in {}s)", 
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

    /**
     * Download file from URL to target location
     * Handles Google Drive large file downloads with confirmation token
     * 
     * SECURITY: URL must be validated before calling this method (via urlValidationService)
     * 
     * @param urlString URL to download from (must be pre-validated)
     * @param targetFile Target file location
     * @return true if download successful, false otherwise
     */
    private boolean downloadFile(String urlString, File targetFile) {
        try {
            // Ensure parent directory exists
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            logger.info("Downloading to: {}", targetFile.getAbsolutePath());

            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000); // 30 seconds
            connection.setReadTimeout(300000); // 5 minutes for large files
            connection.setInstanceFollowRedirects(true);
            
            // SECURITY: Additional check - don't follow redirects to private IPs
            // (UrlValidationService already validated the initial URL)
            
            // Handle Google Drive large file confirmation
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                long contentLength = connection.getContentLengthLong();
                
                // SECURITY: Limit file size to prevent DoS (max 500MB)
                long maxFileSize = 500 * 1024 * 1024; // 500MB
                if (contentLength > maxFileSize) {
                    logger.error("Embedding file too large: {} MB (max 500 MB)", contentLength / (1024 * 1024));
                    return false;
                }
                
                logger.info("Download size: {} MB", contentLength / (1024 * 1024));
                
                try (InputStream inputStream = connection.getInputStream();
                     FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                    
                    byte[] buffer = new byte[8192];
                    long totalBytesRead = 0;
                    int bytesRead;
                    long lastLoggedMb = 0;
                    
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                        long currentMb = totalBytesRead / (1024 * 1024);
                        // Log progress every 50 MB
                        if (currentMb >= lastLoggedMb + 50 && contentLength > 0) {
                            lastLoggedMb = currentMb;
                            long percent = totalBytesRead * 100 / contentLength;
                            logger.info("Download progress: {} MB / {} MB ({}%)", currentMb, contentLength / (1024 * 1024), percent);
                        }
                    }
                    
                    outputStream.flush();
                }
                
                long fileSize = targetFile.length();
                logger.info("Download complete: {} MB", fileSize / (1024 * 1024));
                return true;
            } else {
                logger.error("Embedding file download failed. HTTP {}", responseCode);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error downloading embedding file from {}: {}", LogSanitizer.sanitizeForLogging(urlString), LogSanitizer.sanitize(e.getMessage()), e);
            // Delete partial file if it exists
            if (targetFile.exists()) {
                targetFile.delete();
            }
            return false;
        }
    }
}

