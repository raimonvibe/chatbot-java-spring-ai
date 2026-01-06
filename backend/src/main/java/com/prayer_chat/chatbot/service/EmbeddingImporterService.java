package com.prayer_chat.chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prayer_chat.chatbot.model.BibleVerse;
import com.prayer_chat.chatbot.repository.BibleVerseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Service to import embeddings generated in Google Colab into the database
 * 
 * The embeddings JSON file should be generated using generate-embeddings-colab.py
 */
@Service
public class EmbeddingImporterService {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingImporterService.class);

    private final BibleVerseRepository bibleVerseRepository;
    private final ObjectMapper objectMapper;

    public EmbeddingImporterService(
            BibleVerseRepository bibleVerseRepository,
            ObjectMapper objectMapper) {
        this.bibleVerseRepository = bibleVerseRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Import embeddings from JSON file generated in Google Colab
     * 
     * @param jsonFilePath Path to the bible_embeddings.json file (must be within allowed directory)
     * @return Number of verses updated with embeddings
     */
    @Transactional
    public int importEmbeddings(String jsonFilePath) {
        try {
            logger.info("Starting embedding import from: {}", jsonFilePath);
            
            // SECURITY: Validate and sanitize file path to prevent path traversal attacks
            File file = validateAndResolveFilePath(jsonFilePath);
            
            if (!file.exists()) {
                throw new RuntimeException("File not found: " + jsonFilePath);
            }
            
            if (!file.isFile()) {
                throw new RuntimeException("Path is not a file: " + jsonFilePath);
            }

            // Parse JSON file
            JsonNode root = objectMapper.readTree(new FileInputStream(file));
            JsonNode verses = root.get("verses");
            
            if (verses == null || !verses.isArray()) {
                throw new RuntimeException("Invalid JSON format: 'verses' array not found");
            }

            int updated = 0;
            int skipped = 0;
            List<BibleVerse> batch = new ArrayList<>();

            logger.info("Processing {} verses from JSON file...", verses.size());

            for (JsonNode verseNode : verses) {
                String book = verseNode.get("book").asText();
                int chapter = verseNode.get("chapter").asInt();
                int verse = verseNode.get("verse").asInt();
                
                // Find existing verse
                var verseOpt = bibleVerseRepository.findByBookAndChapterAndVerse(book, chapter, verse);
                
                if (verseOpt.isEmpty()) {
                    skipped++;
                    if (skipped <= 10) { // Only log first 10
                        logger.warn("Verse not found in database: {} {}:{}", book, chapter, verse);
                    }
                    continue;
                }

                BibleVerse bibleVerse = verseOpt.get();
                
                // Parse embedding array
                JsonNode embeddingNode = verseNode.get("embedding");
                if (embeddingNode == null || !embeddingNode.isArray()) {
                    skipped++;
                    continue;
                }

                // Convert to float array
                float[] embedding = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    embedding[i] = (float) embeddingNode.get(i).asDouble();
                }

                // Update verse with embedding
                bibleVerse.setEmbedding(embedding);
                batch.add(bibleVerse);
                updated++;

                // Batch save every 1000 verses
                if (batch.size() >= 1000) {
                    bibleVerseRepository.saveAll(batch);
                    logger.info("Imported batch: {}/{} verses", updated, verses.size());
                    batch.clear();
                }
            }

            // Save remaining verses
            if (!batch.isEmpty()) {
                bibleVerseRepository.saveAll(batch);
            }

            logger.info("✅ Embedding import completed:");
            logger.info("   - Updated: {}", updated);
            logger.info("   - Skipped: {}", skipped);
            logger.info("   - Total processed: {}", updated + skipped);

            return updated;

        } catch (Exception e) {
            logger.error("Error importing embeddings", e);
            throw new RuntimeException("Failed to import embeddings", e);
        }
    }

    /**
     * Validate and resolve file path to prevent path traversal attacks.
     * Only allows files within the application working directory or a specific data directory.
     * 
     * @param filePath User-provided file path
     * @return Validated and resolved File object
     * @throws RuntimeException if path is invalid or contains path traversal attempts
     */
    private File validateAndResolveFilePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new RuntimeException("File path cannot be null or empty");
        }

        // Normalize the path to resolve any ".." or "." components
        Path normalizedPath;
        try {
            normalizedPath = Paths.get(filePath).normalize();
        } catch (Exception e) {
            throw new RuntimeException("Invalid file path: " + filePath, e);
        }

        // Check for path traversal attempts (should not contain ".." after normalization)
        String pathString = normalizedPath.toString();
        if (pathString.contains("..")) {
            throw new RuntimeException("Path traversal detected in file path: " + filePath);
        }

        // Resolve against current working directory or data directory
        // Allow files in:
        // 1. Current working directory (for relative paths like "data/bible_embeddings.json")
        // 2. Absolute paths that are within a reasonable data directory
        // 3. /tmp/data/ directory (for Render deployments where files are uploaded to /tmp)
        File workingDir = new File(System.getProperty("user.dir"));
        File dataDir = new File(workingDir, "data");
        File tmpDataDir = new File("/tmp/data");
        
        File resolvedFile;
        if (normalizedPath.isAbsolute()) {
            // For absolute paths, only allow if they're within working directory, data directory, or /tmp/data
            Path workingPath = workingDir.toPath();
            Path dataPath = dataDir.toPath();
            Path tmpDataPath = tmpDataDir.toPath();
            
            if (!normalizedPath.startsWith(workingPath) && 
                !normalizedPath.startsWith(dataPath) && 
                !normalizedPath.startsWith(tmpDataPath)) {
                throw new RuntimeException("File path must be within application directory, data directory, or /tmp/data: " + filePath);
            }
            resolvedFile = normalizedPath.toFile();
        } else {
            // For relative paths, resolve against working directory
            resolvedFile = workingDir.toPath().resolve(normalizedPath).normalize().toFile();
            
            // Ensure resolved path is still within working directory (prevent escaping)
            Path resolvedPath = resolvedFile.toPath();
            Path workingPath = workingDir.toPath();
            if (!resolvedPath.startsWith(workingPath)) {
                throw new RuntimeException("Resolved path escapes working directory: " + filePath);
            }
        }

        // Additional security: ensure file has .json extension
        String fileName = resolvedFile.getName().toLowerCase();
        if (!fileName.endsWith(".json")) {
            throw new RuntimeException("File must have .json extension: " + filePath);
        }

        logger.debug("Validated file path: {} -> {}", filePath, resolvedFile.getAbsolutePath());
        return resolvedFile;
    }
}

