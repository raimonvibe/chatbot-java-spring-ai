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
     * @param jsonFilePath Path to the bible_embeddings.json file
     * @return Number of verses updated with embeddings
     */
    @Transactional
    public int importEmbeddings(String jsonFilePath) {
        try {
            logger.info("Starting embedding import from: {}", jsonFilePath);
            
            File file = new File(jsonFilePath);
            if (!file.exists()) {
                throw new RuntimeException("File not found: " + jsonFilePath);
            }

            // Parse JSON file
            JsonNode root = objectMapper.readTree(new FileInputStream(file));
            JsonNode verses = root.get("verses");
            
            if (verses == null || !verses.isArray()) {
                throw new RuntimeException("Invalid JSON format: 'verses' array not found");
            }

            int imported = 0;
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
}

