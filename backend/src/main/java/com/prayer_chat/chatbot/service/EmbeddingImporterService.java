package com.prayer_chat.chatbot.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
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
     * Uses STREAMING parsing to minimize memory usage (critical for low-memory environments like Render)
     *
     * @param jsonFilePath Path to the bible_embeddings.json file (must be within allowed directory)
     * @return Number of verses updated with embeddings
     */
    @Transactional
    public int importEmbeddings(String jsonFilePath) {
        try {
            logger.info("Starting STREAMING embedding import from: {}", jsonFilePath);
            logger.info("⚡ Using streaming parser to minimize memory usage");

            // SECURITY: Validate and sanitize file path to prevent path traversal attacks
            File file = validateAndResolveFilePath(jsonFilePath);

            if (!file.exists()) {
                throw new RuntimeException("File not found: " + jsonFilePath);
            }

            if (!file.isFile()) {
                throw new RuntimeException("Path is not a file: " + jsonFilePath);
            }

            int updated = 0;
            int skipped = 0;
            List<BibleVerse> batch = new ArrayList<>();

            // Use smaller batch size to reduce memory pressure
            final int BATCH_SIZE = 100; // Reduced from 1000

            // Stream the JSON file instead of loading it entirely into memory
            try (FileInputStream fis = new FileInputStream(file);
                 JsonParser parser = objectMapper.getFactory().createParser(fis)) {

                // Expect: { "verses": [ ... ] }
                if (parser.nextToken() != JsonToken.START_OBJECT) {
                    throw new RuntimeException("Invalid JSON: Expected object at root");
                }

                // Find "verses" field
                boolean foundVerses = false;
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = parser.getCurrentName();
                    if ("verses".equals(fieldName)) {
                        foundVerses = true;
                        parser.nextToken(); // Move to START_ARRAY
                        if (parser.currentToken() != JsonToken.START_ARRAY) {
                            throw new RuntimeException("Invalid JSON: 'verses' should be an array");
                        }
                        break;
                    }
                    parser.skipChildren();
                }

                if (!foundVerses) {
                    throw new RuntimeException("Invalid JSON format: 'verses' array not found");
                }

                logger.info("📖 Starting to process verses (streaming mode)...");

                // Process each verse object in the array ONE AT A TIME
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (parser.currentToken() != JsonToken.START_OBJECT) {
                        continue;
                    }

                    // Parse one verse object
                    String book = null;
                    Integer chapter = null;
                    Integer verse = null;
                    float[] embedding = null;

                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String field = parser.getCurrentName();
                        parser.nextToken();

                        switch (field) {
                            case "book":
                                book = parser.getValueAsString();
                                break;
                            case "chapter":
                                chapter = parser.getIntValue();
                                break;
                            case "verse":
                                verse = parser.getIntValue();
                                break;
                            case "embedding":
                                if (parser.currentToken() == JsonToken.START_ARRAY) {
                                    List<Float> embList = new ArrayList<>();
                                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                                        embList.add((float) parser.getDoubleValue());
                                    }
                                    embedding = new float[embList.size()];
                                    for (int i = 0; i < embList.size(); i++) {
                                        embedding[i] = embList.get(i);
                                    }
                                }
                                break;
                            default:
                                parser.skipChildren();
                        }
                    }

                    // Validate we got all required fields
                    if (book == null || chapter == null || verse == null || embedding == null) {
                        skipped++;
                        continue;
                    }

                    // Find existing verse in database
                    var verseOpt = bibleVerseRepository.findByBookAndChapterAndVerse(book, chapter, verse);

                    if (verseOpt.isEmpty()) {
                        skipped++;
                        if (skipped <= 10) {
                            logger.warn("Verse not found in database: {} {}:{}", book, chapter, verse);
                        }
                        continue;
                    }

                    BibleVerse bibleVerse = verseOpt.get();
                    bibleVerse.setEmbedding(embedding);
                    batch.add(bibleVerse);
                    updated++;

                    // Batch save and clear memory frequently
                    if (batch.size() >= BATCH_SIZE) {
                        bibleVerseRepository.saveAll(batch);
                        batch.clear();

                        // Log progress every 500 verses
                        if (updated % 500 == 0) {
                            logger.info("📊 Progress: {} verses imported", updated);
                            // Suggest garbage collection to free memory
                            System.gc();
                        }
                    }
                }

                // Save remaining verses
                if (!batch.isEmpty()) {
                    bibleVerseRepository.saveAll(batch);
                    batch.clear();
                }
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

