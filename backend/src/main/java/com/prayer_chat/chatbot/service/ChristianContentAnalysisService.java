package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.model.BibleVerse;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.repository.BibleVerseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for analyzing website content and finding relevant Bible verses using AI-powered semantic matching
 * Replaces the old keyword-based BibleVerseService approach
 */
@Service
public class ChristianContentAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(ChristianContentAnalysisService.class);
    private static final int DEFAULT_MAX_VERSES = 20;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5;

    private final BibleVerseRepository bibleVerseRepository;
    private final EmbeddingModel embeddingModel;
    private final WebsiteAnalysisService websiteAnalysisService;

    public ChristianContentAnalysisService(
            BibleVerseRepository bibleVerseRepository,
            EmbeddingModel embeddingModel,
            WebsiteAnalysisService websiteAnalysisService) {
        this.bibleVerseRepository = bibleVerseRepository;
        this.embeddingModel = embeddingModel;
        this.websiteAnalysisService = websiteAnalysisService;
    }

    /**
     * Analyze website content and find relevant Bible verses using semantic similarity
     * 
     * @param chatbot The chatbot whose website should be analyzed
     * @param maxVerses Maximum number of verses to return (default: 20)
     * @param similarityThreshold Minimum similarity score (0.0-1.0, default: 0.5)
     * @return List of relevant Bible verses ranked by similarity
     */
    @Transactional(readOnly = true)
    public List<BibleVerseMatch> findRelevantVerses(Chatbot chatbot, int maxVerses, double similarityThreshold) {
        try {
            // Step 1: Get website content
            String websiteContent = websiteAnalysisService.getAnalyzedContent(chatbot);
            if (websiteContent == null || websiteContent.trim().isEmpty()) {
                logger.warn("No website content available for chatbot {}", chatbot.getId());
                return Collections.emptyList();
            }

            // Combine website URL, description, and content for better context
            String fullContext = buildContext(chatbot, websiteContent);

            // Step 2: Generate embedding for website content
            logger.debug("Generating embedding for website content (length: {})", fullContext.length());
            float[] websiteEmbedding = embeddingModel.embed(fullContext);
            logger.debug("Generated embedding with {} dimensions", websiteEmbedding.length);

            // Step 3: Get all Bible verses with embeddings
            List<BibleVerse> versesWithEmbeddings = bibleVerseRepository.findVersesWithEmbeddings();
            if (versesWithEmbeddings.isEmpty()) {
                logger.warn("No Bible verses with embeddings found. Run embedding generation first.");
                return Collections.emptyList();
            }

            logger.info("Comparing website content against {} Bible verses", versesWithEmbeddings.size());

            // Step 4: Calculate similarity for each verse
            List<BibleVerseMatch> matches = versesWithEmbeddings.parallelStream()
                    .map(verse -> {
                        double similarity = cosineSimilarity(websiteEmbedding, verse.getEmbedding());
                        return new BibleVerseMatch(verse, similarity);
                    })
                    .filter(match -> match.getSimilarity() >= similarityThreshold)
                    .sorted(Comparator.comparing(BibleVerseMatch::getSimilarity).reversed())
                    .limit(maxVerses)
                    .collect(Collectors.toList());

            logger.info("Found {} relevant verses above threshold {}", matches.size(), similarityThreshold);
            return matches;

        } catch (Exception e) {
            logger.error("Error finding relevant Bible verses for chatbot {}", chatbot.getId(), e);
            throw new RuntimeException("Failed to find relevant Bible verses", e);
        }
    }

    /**
     * Find relevant verses with default parameters
     */
    public List<BibleVerseMatch> findRelevantVerses(Chatbot chatbot) {
        return findRelevantVerses(chatbot, DEFAULT_MAX_VERSES, DEFAULT_SIMILARITY_THRESHOLD);
    }

    /**
     * Get total count of Bible verses in database
     */
    public long getVerseCount() {
        return bibleVerseRepository.count();
    }

    /**
     * Generate embeddings for all Bible verses that don't have them yet
     * This is a one-time operation that should be run after loading Bible data
     */
    @Transactional
    public int generateEmbeddingsForAllVerses() {
        logger.info("Starting embedding generation for all Bible verses...");
        
        List<BibleVerse> versesWithoutEmbeddings = bibleVerseRepository.findAll().stream()
                .filter(v -> v.getEmbedding() == null)
                .collect(Collectors.toList());

        if (versesWithoutEmbeddings.isEmpty()) {
            logger.info("All verses already have embeddings");
            return 0;
        }

        logger.info("Generating embeddings for {} verses", versesWithoutEmbeddings.size());
        int processed = 0;
        int batchSize = 100;

        for (int i = 0; i < versesWithoutEmbeddings.size(); i += batchSize) {
            int end = Math.min(i + batchSize, versesWithoutEmbeddings.size());
            List<BibleVerse> batch = versesWithoutEmbeddings.subList(i, end);

            for (BibleVerse verse : batch) {
                try {
                    // Generate embedding for verse text
                    String verseText = verse.getText();
                    if (verseText == null || verseText.trim().isEmpty()) {
                        continue;
                    }
                    float[] embedding = embeddingModel.embed(verseText);
                    verse.setEmbedding(embedding);
                    processed++;

                    if (processed % 100 == 0) {
                        logger.info("Processed {}/{} verses", processed, versesWithoutEmbeddings.size());
                    }
                } catch (Exception e) {
                    logger.error("Error generating embedding for verse {}", verse.getReference(), e);
                }
            }

            // Save batch
            bibleVerseRepository.saveAll(batch);
        }

        logger.info("Completed embedding generation. Processed {} verses", processed);
        return processed;
    }

    /**
     * Build full context from chatbot information and website content
     */
    private String buildContext(Chatbot chatbot, String websiteContent) {
        StringBuilder context = new StringBuilder();
        
        if (chatbot.getWebsiteUrl() != null) {
            context.append(chatbot.getWebsiteUrl()).append(" ");
        }
        
        if (chatbot.getDescription() != null) {
            context.append(chatbot.getDescription()).append(" ");
        }
        
        if (chatbot.getName() != null) {
            context.append(chatbot.getName()).append(" ");
        }
        
        if (websiteContent != null) {
            // Limit content to first 5000 characters to avoid token limits
            String limitedContent = websiteContent.length() > 5000 
                    ? websiteContent.substring(0, 5000) 
                    : websiteContent;
            context.append(limitedContent);
        }
        
        return context.toString().trim();
    }

    /**
     * Calculate cosine similarity between two embedding vectors
     */
    private double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1 == null || vec2 == null || vec1.length != vec2.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        double denominator = Math.sqrt(norm1) * Math.sqrt(norm2);
        if (denominator == 0.0) {
            return 0.0;
        }

        return dotProduct / denominator;
    }

    /**
     * Inner class to hold verse with similarity score
     */
    public static class BibleVerseMatch {
        private final BibleVerse verse;
        private final double similarity;

        public BibleVerseMatch(BibleVerse verse, double similarity) {
            this.verse = verse;
            this.similarity = similarity;
        }

        public BibleVerse getVerse() {
            return verse;
        }

        public double getSimilarity() {
            return similarity;
        }

        public int getSimilarityPercentage() {
            return (int) Math.round(similarity * 100);
        }
    }
}

