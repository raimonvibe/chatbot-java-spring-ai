package com.prayer_chat.chatbot.util;

/**
 * Shared vector math for embedding similarity. Single source of truth replacing
 * per-service copies in AiChatbotService, ChristianContentAnalysisService and JesusTeachingsService.
 */
public final class VectorMath {

    private VectorMath() {
    }

    /**
     * Cosine similarity between two embedding vectors.
     * Returns 0.0 for null, mismatched-length, or zero-norm vectors (treated as "no similarity"
     * rather than an error, since embeddings can be missing for legacy rows).
     */
    public static double cosineSimilarity(float[] vec1, float[] vec2) {
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
