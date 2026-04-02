package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.dto.JesusTeaching;
import com.prayer_chat.chatbot.model.BibleVerse;
import com.prayer_chat.chatbot.repository.BibleVerseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for finding and using Jesus's direct teachings
 * Used for "What Jesus Would Say" feature
 */
@Service
@Transactional(readOnly = true)
public class JesusTeachingsService {

    private static final Logger logger = LoggerFactory.getLogger(JesusTeachingsService.class);

    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5;
    private static final int DEFAULT_MAX_TEACHINGS = 3;

    private final BibleVerseRepository bibleVerseRepository;
    private final EmbeddingModel embeddingModel;

    public JesusTeachingsService(
            BibleVerseRepository bibleVerseRepository,
            EmbeddingModel embeddingModel) {
        this.bibleVerseRepository = bibleVerseRepository;
        this.embeddingModel = embeddingModel;
    }

    /**
     * Find Jesus's teachings relevant to a topic with default parameters
     *
     * @param topic User's question or website theme
     * @return List of relevant teachings with similarity scores, sorted by relevance
     */
    public List<JesusTeaching> findRelevantTeachings(String topic) {
        return findRelevantTeachings(topic, DEFAULT_MAX_TEACHINGS, DEFAULT_SIMILARITY_THRESHOLD);
    }

    /**
     * Find Jesus's teachings relevant to a topic
     *
     * @param topic User's question or website theme
     * @param maxTeachings Number of teachings to return
     * @return List of relevant teachings with similarity scores, sorted by relevance
     */
    public List<JesusTeaching> findRelevantTeachings(String topic, int maxTeachings) {
        return findRelevantTeachings(topic, maxTeachings, DEFAULT_SIMILARITY_THRESHOLD);
    }

    /**
     * Find Jesus's teachings relevant to a topic with custom threshold
     *
     * @param topic User's question or website theme
     * @param maxTeachings Number of teachings to return
     * @param similarityThreshold Minimum similarity score (0.0-1.0)
     * @return List of relevant teachings with similarity scores, sorted by relevance
     */
    public List<JesusTeaching> findRelevantTeachings(String topic, int maxTeachings, double similarityThreshold) {
        try {
            if (topic == null || topic.trim().isEmpty()) {
                logger.warn("Empty topic provided to findRelevantTeachings");
                return List.of();
            }

            logger.debug("Finding relevant Jesus teachings for topic: {} (max: {}, threshold: {})",
                    topic, maxTeachings, similarityThreshold);

            // Step 1: Generate embedding for the topic
            float[] topicEmbedding = embeddingModel.embed(topic);
            logger.debug("Generated embedding with {} dimensions", topicEmbedding.length);

            // Step 2: Get all Jesus teachings with embeddings
            List<BibleVerse> jesusVerses = bibleVerseRepository.findJesusTeachingsWithEmbeddings();
            if (jesusVerses.isEmpty()) {
                logger.warn("No Jesus teachings with embeddings found. Run verse tagging first.");
                return List.of();
            }

            logger.debug("Comparing topic against {} Jesus teachings", jesusVerses.size());

            // Step 3: Calculate similarity for each teaching and filter/sort
            List<JesusTeaching> teachings = jesusVerses.parallelStream()
                    .map(verse -> {
                        double similarity = cosineSimilarity(topicEmbedding, verse.getEmbedding());
                        return new JesusTeaching(verse, similarity);
                    })
                    .filter(teaching -> teaching.getSimilarity() >= similarityThreshold)
                    .sorted(Comparator.comparing(JesusTeaching::getSimilarity).reversed())
                    .limit(maxTeachings)
                    .collect(Collectors.toList());

            logger.info("Found {} relevant Jesus teachings above threshold {}",
                    teachings.size(), similarityThreshold);

            return teachings;

        } catch (Exception e) {
            logger.error("Error finding relevant Jesus teachings for topic: {}", topic, e);
            return List.of();
        }
    }

    /**
     * Build "What Jesus Would Say" context for AI system prompt
     *
     * @param websiteContent Website description/content
     * @param userQuestion User's question
     * @return Formatted teaching context for system prompt
     */
    public String buildJesusTeachingContext(String websiteContent, String userQuestion) {
        try {
            // Build search query from website content and user question
            StringBuilder searchQuery = new StringBuilder();
            if (websiteContent != null && !websiteContent.trim().isEmpty()) {
                // Limit website content to 500 chars
                String limitedContent = websiteContent.length() > 500
                        ? websiteContent.substring(0, 500) + "..."
                        : websiteContent;
                searchQuery.append("Website context: ").append(limitedContent).append("\n");
            }
            if (userQuestion != null && !userQuestion.trim().isEmpty()) {
                searchQuery.append("User question: ").append(userQuestion);
            }

            String query = searchQuery.toString().trim();
            if (query.isEmpty()) {
                logger.warn("Empty query for buildJesusTeachingContext");
                return "";
            }

            // Find 2-3 relevant teachings
            List<JesusTeaching> teachings = findRelevantTeachings(query, 3, 0.5);

            if (teachings.isEmpty()) {
                logger.debug("No relevant Jesus teachings found for context");
                return "";
            }

            // Build formatted context string
            StringBuilder context = new StringBuilder();
            context.append("\n=== Jesus's Teachings Relevant to This Context ===\n\n");

            for (int i = 0; i < teachings.size(); i++) {
                JesusTeaching teaching = teachings.get(i);
                context.append(String.format("%d. %s (similarity: %.2f)\n", i + 1, teaching.getReference(), teaching.getSimilarity()));
                context.append("   \"").append(teaching.getText()).append("\"\n");
                if (i < teachings.size() - 1) {
                    context.append("\n");
                }
            }

            context.append("\n=== Guidance ===\n");
            context.append("Use these teachings to inform your response. Connect them naturally to the user's question ");
            context.append("and the website's context. Explain how Jesus's wisdom applies to their specific situation. ");
            context.append("Be conversational and practical, not preachy. Show how these teachings relate to their work or question.\n");

            logger.debug("Built Jesus teaching context with {} teachings", teachings.size());
            return context.toString();

        } catch (Exception e) {
            logger.error("Error building Jesus teaching context", e);
            return "";
        }
    }

    /**
     * Get thematic teachings (for first message or general context)
     *
     * @param websiteTheme Main theme of website (e.g., "business", "health", "education")
     * @return Top 3 teachings related to theme
     */
    public List<JesusTeaching> getThematicTeachings(String websiteTheme) {
        if (websiteTheme == null || websiteTheme.trim().isEmpty()) {
            websiteTheme = "general wisdom and guidance";
        }

        logger.debug("Finding thematic teachings for theme: {}", websiteTheme);
        return findRelevantTeachings(websiteTheme, 3, 0.4); // Lower threshold for thematic search
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
}

