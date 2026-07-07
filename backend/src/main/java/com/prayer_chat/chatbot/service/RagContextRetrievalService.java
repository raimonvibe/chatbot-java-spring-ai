package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.model.BibleVerse;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.WebsiteContent;
import com.prayer_chat.chatbot.repository.BibleVerseRepository;
import com.prayer_chat.chatbot.repository.WebsiteContentRepository;
import com.prayer_chat.chatbot.util.LogSanitizer;
import com.prayer_chat.chatbot.util.VectorMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Hybrid RAG context retrieval (strategy-style): DB snapshot + vector search + keyword fallback.
 * Extracted from {@link AiChatbotService} so retrieval can evolve independently of chat orchestration.
 */
@Service
public class RagContextRetrievalService {

    private static final Logger logger = LoggerFactory.getLogger(RagContextRetrievalService.class);

    private static final int VECTOR_SEARCH_TOP_K = 48;
    private static final int VECTOR_MATCHES_MAX = 10;
    private static final int DB_SNAPSHOT_PAGES = 8;
    private static final int MERGED_CONTEXT_DOCS_MAX = 14;
    private static final int DB_SNIPPET_MAX_CHARS = 3500;
    private static final int DB_KEYWORD_FALLBACK_DOCS_MAX = 4;
    private static final int DB_KEYWORD_TOKENS_MAX = 3;
    private static final Pattern SAFE_TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Set<String> STOPWORDS = Set.of(
        "the", "and", "for", "with", "this", "that", "from", "into", "about", "tell", "more", "what", "which", "who",
        "are", "is", "was", "were", "to", "of", "in", "on", "at", "as", "it", "its", "a", "an", "my", "your", "their",
        "website", "site", "page", "pages", "project", "projects"
    );

    private final VectorStore vectorStore;
    private final WebsiteContentRepository websiteContentRepository;
    private final BibleVerseRepository bibleVerseRepository;
    private final EmbeddingModel embeddingModel;

    @Value("${app.rag-observability.enabled:true}")
    private boolean ragObservabilityEnabled;

    @Value("${app.rag-observability.max-docs-logged:8}")
    private int ragObservabilityMaxDocsLogged;

    @Value("${app.rag-observability.max-snippet-chars:220}")
    private int ragObservabilityMaxSnippetChars;

    public RagContextRetrievalService(VectorStore vectorStore,
                                      WebsiteContentRepository websiteContentRepository,
                                      BibleVerseRepository bibleVerseRepository,
                                      EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.websiteContentRepository = websiteContentRepository;
        this.bibleVerseRepository = bibleVerseRepository;
        this.embeddingModel = embeddingModel;
    }

    public List<Document> retrieveRelevantContext(Chatbot chatbot, String userMessage) {
        String wantId = chatbot.getId() != null ? chatbot.getId().toString() : "";
        List<Document> fromDb = documentsFromWebsiteTable(chatbot, DB_SNAPSHOT_PAGES);

        List<Document> fromVector = new ArrayList<>();
        try {
            SearchRequest request = SearchRequest.builder()
                .query(userMessage)
                .topK(VECTOR_SEARCH_TOP_K)
                .similarityThresholdAll()
                .build();
            List<Document> raw = vectorStore.similaritySearch(request);
            fromVector = raw.stream()
                .filter(d -> metadataMatchesChatbot(d, chatbot.getId()))
                .limit(VECTOR_MATCHES_MAX)
                .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Vector similarity search failed for chatbot {}", wantId, e);
        }

        List<String> tokens = keywordTokens(userMessage);
        List<Document> fromDbKeyword = (!tokens.isEmpty())
            ? documentsByKeywordFallback(chatbot, userMessage)
            : List.of();

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Document> merged = new ArrayList<>();
        for (Document d : fromDbKeyword) {
            if (merged.size() >= MERGED_CONTEXT_DOCS_MAX) break;
            String key = contextDedupeKey(d);
            if (seen.add(key)) merged.add(d);
        }
        for (Document d : fromDb) {
            if (merged.size() >= MERGED_CONTEXT_DOCS_MAX) break;
            String key = contextDedupeKey(d);
            if (seen.add(key)) merged.add(d);
        }
        for (Document d : fromVector) {
            if (merged.size() >= MERGED_CONTEXT_DOCS_MAX) break;
            String key = contextDedupeKey(d);
            if (seen.add(key)) merged.add(d);
        }

        if (!fromDb.isEmpty() || !fromVector.isEmpty() || !fromDbKeyword.isEmpty()) {
            logger.debug("Context for chatbot {}: {} keyword DB doc(s), {} DB snapshot doc(s), {} vector hit(s), {} merged doc(s)",
                wantId, fromDbKeyword.size(), fromDb.size(), fromVector.size(), merged.size());
        } else {
            logger.debug("No website content for chatbot {} (analysis may still be running or crawl returned no pages)", wantId);
        }
        return merged;
    }

    public BibleVerse findRelevantBibleVerse(String userMessage, List<Document> websiteDocs, boolean isFirstMessage) {
        try {
            StringBuilder context = new StringBuilder();
            context.append("User question: ").append(userMessage).append("\n");

            if (!websiteDocs.isEmpty()) {
                context.append("Website context: ");
                for (Document doc : websiteDocs) {
                    String docText = doc.getText();
                    if (docText.length() > 500) {
                        docText = docText.substring(0, 500);
                    }
                    context.append(docText).append(" ");
                }
            }

            String searchQuery = context.toString();
            if (searchQuery.length() > 2000) {
                searchQuery = searchQuery.substring(0, 2000);
            }

            float[] queryEmbedding = embeddingModel.embed(searchQuery);
            List<BibleVerse> verses = bibleVerseRepository.findVersesWithEmbeddings();
            if (verses.isEmpty()) {
                logger.warn("No Bible verses with embeddings found in database");
                return null;
            }

            BibleVerse bestMatch = null;
            double bestSimilarity = 0.0;
            for (BibleVerse verse : verses) {
                double similarity = VectorMath.cosineSimilarity(queryEmbedding, verse.getEmbedding());
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestMatch = verse;
                }
            }

            if (bestMatch == null) {
                return null;
            }
            if (isFirstMessage) {
                if (bestSimilarity >= 0.5) {
                    logger.info("Found Bible verse for first message: {} (similarity: {})",
                        bestMatch.getReference(), bestSimilarity);
                    return bestMatch;
                }
            } else {
                if (bestSimilarity >= 0.7) {
                    logger.info("Found highly relevant Bible verse: {} (similarity: {})",
                        bestMatch.getReference(), bestSimilarity);
                    return bestMatch;
                } else if (bestSimilarity >= 0.5) {
                    logger.info("Found moderately relevant Bible verse: {} (similarity: {})",
                        bestMatch.getReference(), bestSimilarity);
                    return bestMatch;
                }
            }
            logger.debug("No sufficiently relevant Bible verse found (best similarity: {})", bestSimilarity);
            return null;
        } catch (Exception e) {
            logger.warn("Failed to find relevant Bible verse: {}", e.getMessage());
            return null;
        }
    }

    public void logRetrievedContext(String traceId, Chatbot chatbot, List<Document> relevantDocs) {
        if (!ragObservabilityEnabled) return;
        int limit = Math.max(1, ragObservabilityMaxDocsLogged);
        int snippetMax = Math.max(40, ragObservabilityMaxSnippetChars);
        for (int i = 0; i < relevantDocs.size() && i < limit; i++) {
            Document doc = relevantDocs.get(i);
            logger.info(
                "RAG_OBS chunk trace={} chatbotId={} rank={} source={} score={} title={} url={} snippet={}",
                traceId,
                chatbot != null ? chatbot.getId() : null,
                i + 1,
                inferRetrievalSource(doc),
                readSimilarityScore(doc),
                sanitizeMetadataValue(doc, "title"),
                sanitizeMetadataValue(doc, "url"),
                snippetForLog(doc.getText(), snippetMax)
            );
        }
        logger.info(
            "RAG_OBS retrieval_summary trace={} chatbotId={} totalRetrieved={} logged={}",
            traceId,
            chatbot != null ? chatbot.getId() : null,
            relevantDocs.size(),
            Math.min(relevantDocs.size(), limit)
        );
    }

    public void logAnswerGrounding(String traceId, List<Document> relevantDocs, String aiText) {
        if (!ragObservabilityEnabled) return;
        String safeAnswer = aiText == null ? "" : aiText.toLowerCase(Locale.ROOT);
        int matchedDocs = 0;
        int inspected = 0;
        int limit = Math.max(1, ragObservabilityMaxDocsLogged);
        for (Document doc : relevantDocs) {
            if (inspected >= limit) break;
            inspected++;
            String title = metadataLower(doc, "title");
            String url = metadataLower(doc, "url");
            boolean used = (!title.isEmpty() && safeAnswer.contains(title))
                || (!url.isEmpty() && safeAnswer.contains(url));
            if (used) matchedDocs++;
        }
        logger.info(
            "RAG_OBS grounding trace={} referencedRetrievedDocs={} inspectedDocs={} answerPreview={}",
            traceId,
            matchedDocs,
            inspected,
            snippetForLog(aiText, 260)
        );
    }

    private List<Document> documentsFromWebsiteTable(Chatbot chatbot, int limit) {
        List<WebsiteContent> rows = websiteContentRepository.findByChatbot(chatbot).stream()
            .sorted(Comparator.comparing(WebsiteContent::getId, Comparator.nullsLast(Comparator.naturalOrder())))
            .limit(limit)
            .collect(Collectors.toList());
        List<Document> out = new ArrayList<>();
        for (WebsiteContent c : rows) {
            String text = (c.getTitle() != null ? c.getTitle() + ". " : "") + (c.getContent() != null ? c.getContent() : "");
            if (text.trim().length() < 8) continue;
            if (text.length() > DB_SNIPPET_MAX_CHARS) {
                text = text.substring(0, DB_SNIPPET_MAX_CHARS) + "...";
            }
            out.add(new Document(text, Map.of(
                "chatbotId", chatbot.getId().toString(),
                "url", c.getUrl() != null ? c.getUrl() : "",
                "title", c.getTitle() != null ? c.getTitle() : "",
                "retrievalSource", "db_snapshot"
            )));
        }
        return out;
    }

    private List<Document> documentsByKeywordFallback(Chatbot chatbot, String userMessage) {
        if (chatbot == null) return List.of();
        List<String> tokens = keywordTokens(userMessage);
        if (tokens.isEmpty()) return List.of();

        List<Document> out = new ArrayList<>();
        for (String token : tokens) {
            try {
                var page = websiteContentRepository.searchByChatbotAndKeyword(
                    chatbot,
                    token,
                    PageRequest.of(0, DB_KEYWORD_FALLBACK_DOCS_MAX)
                );
                if (page == null) {
                    continue;
                }
                for (WebsiteContent c : page.getContent()) {
                    if (c == null) continue;
                    String text = (c.getTitle() != null ? c.getTitle() + ". " : "") + (c.getContent() != null ? c.getContent() : "");
                    if (text.trim().length() < 8) continue;
                    if (text.length() > DB_SNIPPET_MAX_CHARS) {
                        text = text.substring(0, DB_SNIPPET_MAX_CHARS) + "...";
                    }
                    out.add(new Document(text, Map.of(
                        "chatbotId", chatbot.getId() != null ? chatbot.getId().toString() : "",
                        "url", c.getUrl() != null ? c.getUrl() : "",
                        "title", c.getTitle() != null ? c.getTitle() : "",
                        "retrievalSource", "db_keyword"
                    )));
                    if (out.size() >= DB_KEYWORD_FALLBACK_DOCS_MAX) return out;
                }
            } catch (Exception e) {
                logger.warn("DB keyword fallback search failed for chatbot {}: {}", chatbot.getId(), e.getMessage());
            }
        }
        return out;
    }

    private static List<String> keywordTokens(String userMessage) {
        if (userMessage == null) return List.of();
        String msg = userMessage.trim();
        if (msg.isEmpty()) return List.of();
        if (msg.length() > 500) msg = msg.substring(0, 500);

        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String raw : SAFE_TOKEN_SPLIT.split(msg.toLowerCase(Locale.ROOT))) {
            if (tokens.size() >= DB_KEYWORD_TOKENS_MAX) break;
            if (raw == null) continue;
            String t = raw.trim();
            if (t.length() < 3 || t.length() > 24) continue;
            if (STOPWORDS.contains(t)) continue;
            if (!t.matches("^[\\p{L}\\p{N}][\\p{L}\\p{N}_-]*$")) continue;
            tokens.add(t);
        }
        return new ArrayList<>(tokens);
    }

    private static String contextDedupeKey(Document d) {
        Object url = d.getMetadata() != null ? d.getMetadata().get("url") : null;
        if (url != null && !url.toString().isBlank()) {
            String u = url.toString().trim();
            if (u.endsWith("/")) {
                u = u.length() > 1 ? u.substring(0, u.length() - 1) : u;
            }
            return "u:" + u;
        }
        String t = d.getText();
        if (t == null || t.isBlank()) {
            return "e:" + System.identityHashCode(d);
        }
        return "t:" + t.substring(0, Math.min(120, t.length()));
    }

    private static boolean metadataMatchesChatbot(Document d, Long chatbotId) {
        if (d.getMetadata() == null || chatbotId == null) return false;
        Object v = d.getMetadata().get("chatbotId");
        if (v == null) return false;
        return chatbotId.toString().equals(String.valueOf(v));
    }

    private static String inferRetrievalSource(Document doc) {
        Object explicit = doc.getMetadata() != null ? doc.getMetadata().get("retrievalSource") : null;
        if (explicit != null && !explicit.toString().isBlank()) {
            return explicit.toString();
        }
        String score = readSimilarityScore(doc);
        if (!"n/a".equals(score)) return "vector";
        return "unknown";
    }

    private static String readSimilarityScore(Document doc) {
        if (doc.getMetadata() == null) return "n/a";
        String[] candidateKeys = {"score", "similarity", "distance"};
        for (String key : candidateKeys) {
            Object value = doc.getMetadata().get(key);
            if (value != null) {
                return LogSanitizer.sanitizeForLogging(String.valueOf(value));
            }
        }
        return "n/a";
    }

    private static String sanitizeMetadataValue(Document doc, String key) {
        if (doc.getMetadata() == null) return "";
        Object value = doc.getMetadata().get(key);
        if (value == null) return "";
        return LogSanitizer.sanitizeForLogging(String.valueOf(value));
    }

    private static String metadataLower(Document doc, String key) {
        String value = sanitizeMetadataValue(doc, key);
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String snippetForLog(String text, int maxChars) {
        if (text == null) return "";
        String compact = text.replaceAll("\\s+", " ").trim();
        return LogSanitizer.sanitizeForLogging(LogSanitizer.truncate(compact, maxChars));
    }
}
