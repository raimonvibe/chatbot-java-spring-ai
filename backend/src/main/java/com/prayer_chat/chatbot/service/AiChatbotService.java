package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.model.*;
import com.prayer_chat.chatbot.repository.BibleVerseRepository;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.repository.ConversationRepository;
import com.prayer_chat.chatbot.repository.MessageRepository;
import com.prayer_chat.chatbot.repository.WebsiteContentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for AI-powered chatbot interactions using Spring AI
 */
@Service
@Transactional
public class AiChatbotService {

    /** Accurate description of Raimonvibe; only use when user explicitly asks about the platform/creator. */
    private static final String ABOUT_RAIMONVIBE =
        "About the platform creator (mention ONLY when the user explicitly asks who built this tool, about the platform, or about Raimonvibe): "
        + "Raimonvibe (raimonvibe.com) is a freelance web design and software engineering practice. "
        + "It offers responsive websites for businesses, blogs about coding and 3D printing, and software projects. "
        + "Contact: info@raimonvibe.com. Do not describe it as a Christian or faith-based business. "
        + "For 'this site', 'the site', 'this website', or 'tell me about this site'—answer ONLY from the website content below (the site this chatbot was built from), not about Raimonvibe.";
    
    private static final Logger logger = LoggerFactory.getLogger(AiChatbotService.class);
    
    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final ChatbotRepository chatbotRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final WebsiteContentRepository websiteContentRepository;
    private final BibleVerseRepository bibleVerseRepository;
    private final WebhookService webhookService; // NEW FEATURE
    private final JesusTeachingsService jesusTeachingsService; // NEW FEATURE: Jesus Teachings

    @Value("${app.chatbot.max-conversation-history:10}")
    private int maxConversationHistory;

    @Value("${app.chatbot.default-language:en}")
    private String defaultLanguage;

    @Autowired
    public AiChatbotService(ChatClient chatClient, VectorStore vectorStore, EmbeddingModel embeddingModel,
                           ChatbotRepository chatbotRepository, ConversationRepository conversationRepository,
                           MessageRepository messageRepository, WebsiteContentRepository websiteContentRepository,
                           BibleVerseRepository bibleVerseRepository, WebhookService webhookService,
                           JesusTeachingsService jesusTeachingsService) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.chatbotRepository = chatbotRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.websiteContentRepository = websiteContentRepository;
        this.bibleVerseRepository = bibleVerseRepository;
        this.webhookService = webhookService; // NEW FEATURE
        this.jesusTeachingsService = jesusTeachingsService; // NEW FEATURE: Jesus Teachings
    }
    
    /**
     * Remove all vector store documents for a chatbot. Call before re-analyzing or when deleting
     * the chatbot so a new chatbot reusing the same ID does not inherit previous content.
     */
    public void deleteVectorStoreDocumentsForChatbot(Long chatbotId) {
        if (chatbotId == null) return;
        try {
            Chatbot ref = new Chatbot();
            ref.setId(chatbotId);
            List<String> ids = websiteContentRepository.findByChatbot(ref).stream()
                .map(WebsiteContent::getVectorId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
            if (!ids.isEmpty()) {
                vectorStore.delete(ids);
                logger.debug("Removed {} vector store documents for chatbot {}", ids.size(), chatbotId);
            }
        } catch (Exception e) {
            logger.warn("Could not remove vector store documents for chatbot {}: {}", chatbotId, e.getMessage());
        }
    }

    /**
     * Process a user message and generate a response
     */
    public ChatResponse processMessage(Long chatbotId, String userMessage, String sessionId, 
                                     String userLanguage, String userIp, String userAgent) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Get chatbot
            Chatbot chatbot = chatbotRepository.findById(chatbotId)
                .orElseThrow(() -> new RuntimeException("Chatbot not found"));
            
            if (!chatbot.getIsActive()) {
                throw new RuntimeException("Chatbot is not active");
            }
            
            // Get or create conversation
            Conversation conversation = getOrCreateConversation(chatbot, sessionId, userLanguage, userIp, userAgent);
            
            // Save user message
            com.prayer_chat.chatbot.model.Message userMsg = new com.prayer_chat.chatbot.model.Message(conversation, com.prayer_chat.chatbot.model.Message.MessageType.TEXT, userMessage, true);
            messageRepository.save(userMsg);

            // NEW FEATURE: Send webhook event for new message
            webhookService.sendNewMessageEvent(chatbot, conversation, userMsg);

            // Generate AI response
            String aiResponse = generateResponse(chatbot, conversation, userMessage, userLanguage);

            // Calculate response time
            long responseTime = System.currentTimeMillis() - startTime;

            // Save AI response
            com.prayer_chat.chatbot.model.Message aiMsg = new com.prayer_chat.chatbot.model.Message(conversation, com.prayer_chat.chatbot.model.Message.MessageType.TEXT, aiResponse, false);
            aiMsg.setResponseTimeMs((int) responseTime);
            messageRepository.save(aiMsg);

            // NEW FEATURE: Send webhook event for bot response
            webhookService.sendNewMessageEvent(chatbot, conversation, aiMsg);
            
            // Create chat response
            ChatResponse response = new ChatResponse(List.of(new org.springframework.ai.chat.model.Generation(new AssistantMessage(aiResponse))));
            
            logger.info("Processed message for chatbot {} in {}ms", chatbotId, responseTime);
            return response;
            
        } catch (Exception e) {
            logger.error("Error processing message for chatbot {}", chatbotId, e);
            throw new RuntimeException("Failed to process message: " + e.getMessage());
        }
    }
    
    /**
     * Get or create a conversation
     */
    private Conversation getOrCreateConversation(Chatbot chatbot, String sessionId, 
                                               String userLanguage, String userIp, String userAgent) {
        
        Conversation conversation = conversationRepository.findByChatbotAndSessionId(chatbot, sessionId)
            .orElse(null);
        
        boolean isNewConversation = (conversation == null);

        if (isNewConversation) {
            conversation = new Conversation(chatbot, sessionId);
            conversation.setUserLanguage(userLanguage != null ? userLanguage : defaultLanguage);
            conversation.setUserIp(userIp);
            conversation.setUserAgent(userAgent);
            conversation = conversationRepository.save(conversation);

            // NEW FEATURE: Send webhook event for new conversation
            webhookService.sendNewConversationEvent(chatbot, conversation);
        }

        return conversation;
    }
    
    /**
     * Generate AI response using RAG (Retrieval Augmented Generation)
     */
    private String generateResponse(Chatbot chatbot, Conversation conversation, String userMessage, String userLanguage) {

        // Retrieve relevant context from vector store
        List<Document> relevantDocs = retrieveRelevantContext(chatbot, userMessage);

        // Build conversation history
        List<com.prayer_chat.chatbot.model.Message> recentMessages = getRecentMessages(conversation);

        // Check if this is the first message (for Christian greeting)
        boolean isFirstMessage = recentMessages.isEmpty();

        // Find relevant Bible verse if Christian messaging is enabled
        BibleVerse relevantVerse = null;
        if (chatbot.getChristianMessagingEnabled() != null && chatbot.getChristianMessagingEnabled()) {
            relevantVerse = findRelevantBibleVerse(userMessage, relevantDocs, isFirstMessage);
        }

        // NEW: Find Jesus's teachings if enabled
        String jesusTeachingContext = null;
        if (chatbot.getJesusTeachingsEnabled() != null && chatbot.getJesusTeachingsEnabled()) {
            String websiteContent = buildWebsiteContext(relevantDocs);
            jesusTeachingContext = jesusTeachingsService.buildJesusTeachingContext(
                    websiteContent,
                    userMessage
            );
        }

        // Create system prompt with context
        String systemPrompt = buildSystemPrompt(chatbot, relevantDocs, userLanguage, relevantVerse, jesusTeachingContext, isFirstMessage);

        // Add Christian greeting instruction for first message
        if (isFirstMessage && chatbot.getChristianMessagingEnabled() != null && chatbot.getChristianMessagingEnabled()) {
            systemPrompt += "\nIMPORTANT: This is the first message. Start your response with a warm Christian greeting (e.g., 'Welcome! God's blessings to you!', 'Greetings in Christ!', 'Peace be with you!').\n";
        }

        // Build messages for the chat
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));

        // Add conversation history
        for (com.prayer_chat.chatbot.model.Message msg : recentMessages) {
            if (msg.getIsUserMessage()) {
                messages.add(new UserMessage(msg.getContent()));
            } else {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        // Add current user message
        messages.add(new UserMessage(userMessage));

        // Generate response
        try {
            Prompt prompt = new Prompt(messages);
            ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
            return response.getResult().getOutput().getText();
        } catch (IllegalStateException e) {
            // This usually means ChatModel is not configured (missing API key)
            if (e.getMessage() != null && (e.getMessage().contains("ANTHROPIC_API_KEY") || 
                                            e.getMessage().contains("ChatModel") || 
                                            e.getMessage().contains("not properly configured"))) {
                logger.error("AI service not configured. Check ANTHROPIC_API_KEY environment variable.");
                throw new RuntimeException("AI service is not configured. Please ensure ANTHROPIC_API_KEY is set.", e);
            }
            throw e;
        } catch (Exception e) {
            logger.error("Error generating AI response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate AI response: " + e.getMessage(), e);
        }
    }
    
    private static final int VECTOR_SEARCH_TOP_K = 48;
    private static final int VECTOR_MATCHES_MAX = 10;
    private static final int DB_SNAPSHOT_PAGES = 8;
    private static final int MERGED_CONTEXT_DOCS_MAX = 14;
    private static final int DB_SNIPPET_MAX_CHARS = 3500;

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
        if (d.getMetadata() == null || chatbotId == null) {
            return false;
        }
        Object v = d.getMetadata().get("chatbotId");
        if (v == null) {
            return false;
        }
        return chatbotId.toString().equals(String.valueOf(v));
    }

    /**
     * Stored pages from DB as {@link Document}s (always available after crawl, even if vector indexing lags).
     */
    private List<Document> documentsFromWebsiteTable(Chatbot chatbot, int limit) {
        List<WebsiteContent> rows = websiteContentRepository.findByChatbot(chatbot).stream()
            .sorted(Comparator.comparing(WebsiteContent::getId, Comparator.nullsLast(Comparator.naturalOrder())))
            .limit(limit)
            .collect(Collectors.toList());
        List<Document> out = new ArrayList<>();
        for (WebsiteContent c : rows) {
            String text = (c.getTitle() != null ? c.getTitle() + ". " : "") + (c.getContent() != null ? c.getContent() : "");
            if (text.trim().length() < 8) {
                continue;
            }
            if (text.length() > DB_SNIPPET_MAX_CHARS) {
                text = text.substring(0, DB_SNIPPET_MAX_CHARS) + "...";
            }
            out.add(new Document(text, Map.of(
                "chatbotId", chatbot.getId().toString(),
                "url", c.getUrl() != null ? c.getUrl() : "",
                "title", c.getTitle() != null ? c.getTitle() : ""
            )));
        }
        return out;
    }

    /**
     * Hybrid context: stored website pages (broad coverage for "about this site") plus vector matches (query-specific).
     * Vector hits are post-filtered by {@code chatbotId} in-process so tenant isolation does not depend on store filter syntax.
     */
    private List<Document> retrieveRelevantContext(Chatbot chatbot, String userMessage) {
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
            logger.warn("Vector similarity search failed for chatbot {}: {}", wantId, e.getMessage());
        }

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Document> merged = new ArrayList<>();
        for (Document d : fromDb) {
            if (merged.size() >= MERGED_CONTEXT_DOCS_MAX) {
                break;
            }
            String key = contextDedupeKey(d);
            if (seen.add(key)) {
                merged.add(d);
            }
        }
        for (Document d : fromVector) {
            if (merged.size() >= MERGED_CONTEXT_DOCS_MAX) {
                break;
            }
            String key = contextDedupeKey(d);
            if (seen.add(key)) {
                merged.add(d);
            }
        }

        if (!fromDb.isEmpty() || !fromVector.isEmpty()) {
            logger.debug("Context for chatbot {}: {} DB page(s), {} vector hit(s), {} merged doc(s)",
                wantId, fromDb.size(), fromVector.size(), merged.size());
        } else {
            logger.debug("No website content for chatbot {} (analysis may still be running or crawl returned no pages)", wantId);
        }
        return merged;
    }
    
    /**
     * Get recent conversation history
     */
    private List<com.prayer_chat.chatbot.model.Message> getRecentMessages(Conversation conversation) {
        return messageRepository.findByConversationOrderByCreatedAtDesc(conversation)
            .stream()
            .limit(maxConversationHistory)
            .sorted(Comparator.comparing(com.prayer_chat.chatbot.model.Message::getCreatedAt))
            .collect(Collectors.toList());
    }

    /**
     * Find relevant Bible verse for the current conversation context
     * Uses smart threshold logic:
     * - First message: Always find a verse (threshold 0.5)
     * - Other messages: Only if highly relevant (try 0.7, fallback to 0.5)
     *
     * @param userMessage The user's question
     * @param websiteDocs Relevant website content documents
     * @param isFirstMessage Whether this is the first message in the conversation
     * @return BibleVerse if relevant one found, null otherwise
     */
    private BibleVerse findRelevantBibleVerse(String userMessage, List<Document> websiteDocs, boolean isFirstMessage) {
        try {
            // Build search context: combine user message + website content
            StringBuilder context = new StringBuilder();
            context.append("User question: ").append(userMessage).append("\n");

            if (!websiteDocs.isEmpty()) {
                context.append("Website context: ");
                for (Document doc : websiteDocs) {
                    // Limit each doc to 500 chars to avoid token limits
                    String docText = doc.getText();
                    if (docText.length() > 500) {
                        docText = docText.substring(0, 500);
                    }
                    context.append(docText).append(" ");
                }
            }

            // Limit total context to 2000 chars
            String searchQuery = context.toString();
            if (searchQuery.length() > 2000) {
                searchQuery = searchQuery.substring(0, 2000);
            }

            // Generate embedding for the query
            float[] queryEmbedding = embeddingModel.embed(searchQuery);

            // Get all Bible verses with embeddings
            List<BibleVerse> verses = bibleVerseRepository.findVersesWithEmbeddings();

            if (verses.isEmpty()) {
                logger.warn("No Bible verses with embeddings found in database");
                return null;
            }

            // Calculate similarity for each verse and find best match
            BibleVerse bestMatch = null;
            double bestSimilarity = 0.0;

            for (BibleVerse verse : verses) {
                double similarity = cosineSimilarity(queryEmbedding, verse.getEmbedding());
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestMatch = verse;
                }
            }

            // Smart threshold logic (bestMatch is non-null when bestSimilarity > 0)
            if (bestMatch == null) {
                return null;
            }
            if (isFirstMessage) {
                // First message: Use verse if similarity >= 0.5 (moderately relevant)
                if (bestSimilarity >= 0.5) {
                    logger.info("Found Bible verse for first message: {} (similarity: {})",
                        bestMatch.getReference(), bestSimilarity);
                    return bestMatch;
                }
            } else {
                // Subsequent messages: Use verse only if highly or moderately relevant
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
            logger.error("Error finding relevant Bible verse", e);
            return null;
        }
    }

    /**
     * Calculate cosine similarity between two embedding vectors
     */
    private double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Build system prompt with context and dynamic Bible verse
     */
    /**
     * Build website context string from documents
     */
    private String buildWebsiteContext(List<Document> relevantDocs) {
        if (relevantDocs == null || relevantDocs.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        for (Document doc : relevantDocs) {
            String docText = doc.getText();
            // Limit each doc to 500 chars to avoid token limits
            if (docText.length() > 500) {
                docText = docText.substring(0, 500) + "...";
            }
            context.append(docText).append(" ");
        }
        return context.toString().trim();
    }

    private String buildSystemPrompt(Chatbot chatbot, List<Document> relevantDocs, String userLanguage,
                                     BibleVerse relevantVerse, String jesusTeachingContext, boolean isFirstMessage) {
        StringBuilder prompt = new StringBuilder();

        // RAG-style: retrieved excerpts ground site/business answers; general topics can be discussed freely (no fake site facts).
        prompt.append("You are an AI assistant for ").append(chatbot.getName()).append(".\n");
        String websiteUrl = chatbot.getWebsiteUrl() != null ? chatbot.getWebsiteUrl() : "";
        String safeUrl = safeUrlForPrompt(websiteUrl);
        prompt.append("You are a retrieval-augmented (RAG) assistant: the sections below titled \"Retrieved website content\" are excerpts ");
        prompt.append("from the crawled site");
        if (!safeUrl.isEmpty()) {
            prompt.append(" (").append(safeUrl).append(")");
        }
        prompt.append(". They are your primary evidence for anything specific to that business, its pages, or what appears on that website.\n");
        prompt.append("How to respond:\n");
        prompt.append("- Site- or business-related questions (e.g. \"this site\", \"this website\", \"what do you offer\", pricing, hours, team, page content): ");
        prompt.append("ground your answer in the retrieved excerpts when they contain the information. If the excerpts are silent or incomplete, say so—do not invent or guess business- or site-specific facts.\n");
        prompt.append("- General conversation (faith, life, ideas, topics not tied to that site's pages): answer helpfully and naturally. ");
        prompt.append("You are not required to steer every message back to the website; only avoid claiming specific facts about this business or site unless the excerpts support them.\n");
        prompt.append("- Do not describe the chatbot platform or vendor as if it were the customer's business; the user is visiting ").append(chatbot.getName());
        prompt.append(" for that site's purpose.\n");
        prompt.append("Tone: friendly, professional, and clear. When site-specific details are missing, suggest contacting the business");
        if (!safeUrl.isEmpty()) {
            prompt.append(" or visiting ").append(safeUrl);
        }
        prompt.append(".\n");

        // Retrieved content first (classic RAG ordering: evidence before instructions that reference it)
        if (!relevantDocs.isEmpty()) {
            prompt.append("\n--- Retrieved website content (RAG context — prioritize for site/business questions) ---\n");
            int totalContentChars = 0;
            for (Document doc : relevantDocs) {
                String text = doc.getText();
                if (text != null) totalContentChars += text.length();
                prompt.append(text != null ? text : "").append("\n\n");
            }
            prompt.append("--- End of retrieved website content ---\n");
            // When content is minimal (e.g. SPA with only title "frontend"), give a helpful reply instead of "I don't have much"
            if (totalContentChars < 400) {
                prompt.append("\nNote: The content above is minimal (e.g. only a page title). This often happens with modern single-page apps (Vercel, React, etc.). ");
                prompt.append("If the user asks about the site (e.g. 'tell me about this site'), respond warmly: say you could only see limited text from the scan, ");
                prompt.append("suggest they visit the site directly").append(safeUrl.isEmpty() ? "" : " at " + safeUrl).append(" for full details, and offer to help with other questions. ");
                prompt.append("Do not claim the site has no purpose—just that your view of it is limited. Keep the tone friendly and helpful.\n");
            }
        } else {
            // No crawl/DB text yet (analysis still running, failed, or only empty pages saved). Never invent site facts.
            prompt.append("\n--- No retrieved website content yet (RAG context empty) ---\n");
            prompt.append("There are no indexed page excerpts yet (analysis may still be running, or the crawler found no usable text). ");
            prompt.append("Do NOT invent or guess information about this business or website. ");
            prompt.append("If the user asks what this site is about, explain that you do not have indexed content yet and suggest trying again later or contacting the site owner. ");
            prompt.append("For general questions unrelated to site facts, you may still answer helpfully—just do not claim specific details about this business or its pages.\n");
        }

        prompt.append("\n").append(ABOUT_RAIMONVIBE).append("\n");

        // Add Christian persona and values if enabled
        if (chatbot.getChristianMessagingEnabled() != null && chatbot.getChristianMessagingEnabled()) {
            prompt.append("\nChristian Persona and Values:\n");
            prompt.append("- You are a warm, down-to-earth friend who loves Jesus.\n");
            prompt.append("- Speak like you're sitting next to someone on a couch: casual, honest, and human-sounding, not like a preacher or lecturer.\n");
            prompt.append("- Listen first, reflect what the user shares, and respond with empathy and practical help.\n");
            prompt.append("- Never judge or talk down to the user; always respond with kindness, patience, and respect.\n");
            prompt.append("- When it genuinely helps, you may gently weave in a Bible verse or story, but only if it fits naturally and is relevant. Prefer real verses from the context above; do not invent references.\n");
            prompt.append("- Remember that you are an AI assistant, not a pastor, doctor, lawyer, or therapist, and you cannot replace professional or pastoral care.\n");

            // Add dynamically selected Bible verse (if found and relevant)
            if (relevantVerse != null) {
                prompt.append("\n📖 Relevant Scripture for this conversation:\n");
                prompt.append(relevantVerse.getReference()).append(" - \"").append(relevantVerse.getText()).append("\"\n");
                prompt.append("\nInstructions for using this verse:\n");
                if (isFirstMessage) {
                    prompt.append("- This is the first message. Naturally incorporate this verse into your introduction if it relates to the business's mission or values.\n");
                } else {
                    prompt.append("- Only mention this verse if it's truly relevant to the user's question.\n");
                    prompt.append("- When citing the verse, briefly explain how it connects to what the user is asking about.\n");
                    prompt.append("- Do not force it—if it doesn't fit naturally, leave it out.\n");
                }
            }

            // Gentle, diverse ending style guidance – ALWAYS add a short Christian encouragement
            prompt.append("\nEnding style:\n");
            prompt.append("- End each response with a brief Christian blessing or encouragement (for example: \"God bless you\", \"Grace and peace to you\", \"May the Lord give you strength\", \"Jesus be near to you today\", \"The Lord be with you\").\n");
            prompt.append("- Vary your blessings so they do not sound the same every time.\n");
            prompt.append("- You may also end with a gentle question or a simple \"I'm here if you want to share more\" together with the blessing when it fits the conversation.\n");
        }

        // NEW: Add Jesus's teachings section if enabled
        if (jesusTeachingContext != null && !jesusTeachingContext.trim().isEmpty()) {
            prompt.append("\n").append("=".repeat(50)).append("\n");
            prompt.append("📖 WHAT JESUS WOULD SAY:\n");
            prompt.append(jesusTeachingContext).append("\n");
            prompt.append("=".repeat(50)).append("\n");

            prompt.append("\nInstructions for using Jesus's teachings:\n");
            prompt.append("- Draw gentle inspiration from the teachings above to encourage and comfort the user.\n");
            prompt.append("- Explain briefly how Jesus's wisdom applies to this situation, using conversational, down-to-earth language (avoid long sermons or lectures).\n");
            prompt.append("- Connect the teachings to the specific question or context in a natural way; do not force a \"Jesus perspective\" into every answer.\n");
            prompt.append("- Be authentic and respectful in how you mention Jesus and Scripture.\n");

            if (isFirstMessage) {
                prompt.append("- For the first message, you may include a brief 'Jesus's perspective' on this business/website if it fits naturally.\n");
            } else {
                prompt.append("- Only include Jesus's perspective when it genuinely fits the user's question.\n");
            }
        }

        // Add custom prompt if configured
        if (chatbot.getCustomPrompt() != null && !chatbot.getCustomPrompt().trim().isEmpty()) {
            prompt.append("\nAdditional instructions: ").append(chatbot.getCustomPrompt()).append("\n");
        }

        // Add language-specific instructions
        if (userLanguage != null && !userLanguage.equals("en")) {
            prompt.append("\nRespond in ").append(getLanguageName(userLanguage)).append(".\n");
        }

        return prompt.toString();
    }

    /**
     * Returns a safe URL string for inclusion in the system prompt (origin only: scheme + host).
     * Prevents prompt injection via path, query, fragment, or newlines in websiteUrl.
     * Max length enforced to avoid oversized prompts.
     */
    static String safeUrlForPrompt(String url) {
        if (url == null || url.isBlank()) return "";
        String s = url.trim();
        final int maxLen = 500;
        if (s.length() > maxLen) s = s.substring(0, maxLen);
        try {
            URI uri = URI.create(s);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) return "";
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return "";
            int port = uri.getPort();
            if (port == -1 || port == 80 && "http".equalsIgnoreCase(scheme) || port == 443 && "https".equalsIgnoreCase(scheme)) {
                return scheme + "://" + host;
            }
            return scheme + "://" + host + ":" + port;
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Get language name from code
     */
    private String getLanguageName(String languageCode) {
        Map<String, String> languages = Map.of(
            "en", "English",
            "es", "Spanish", 
            "fr", "French",
            "de", "German",
            "it", "Italian",
            "pt", "Portuguese",
            "ru", "Russian",
            "zh", "Chinese",
            "ja", "Japanese",
            "ko", "Korean"
        );
        return languages.getOrDefault(languageCode, "English");
    }
    
    /** Batch size for indexing to limit peak memory (avoids OOM after analysis on small instances). */
    private static final int INDEXING_BATCH_SIZE = 10;
    /** Max batches to prevent unbounded iteration DoS (5000 content items). */
    private static final int INDEXING_MAX_BATCHES = 500;

    /**
     * Index website content for a chatbot. Processes in batches to reduce peak memory after analysis.
     * Uses fixed batch size and max-batch cap for security and stability.
     */
    public void indexWebsiteContent(Chatbot chatbot) {
        if (chatbot == null || chatbot.getId() == null) {
            logger.warn("Indexing skipped: chatbot or id is null");
            return;
        }
        logger.info("Starting content indexing for chatbot: {}", chatbot.getId());
        int page = 0;
        int totalIndexed = 0;
        while (page < INDEXING_MAX_BATCHES) {
            var batch = websiteContentRepository.findByChatbot(chatbot, PageRequest.of(page, INDEXING_BATCH_SIZE));
            if (batch.isEmpty()) break;
            for (WebsiteContent content : batch) {
                try {
                    Document document = new Document(
                        content.getContent(),
                        Map.of(
                            "chatbotId", chatbot.getId().toString(),
                            "url", content.getUrl(),
                            "title", content.getTitle(),
                            "language", content.getLanguage() != null ? content.getLanguage() : "en"
                        )
                    );
                    vectorStore.add(List.of(document));
                    content.setIsIndexed(true);
                    content.setVectorId(document.getId());
                    websiteContentRepository.save(content);
                    totalIndexed++;
                    logger.debug("Indexed content: {}", content.getUrl());
                } catch (Exception e) {
                    logger.error("Failed to index content: {}", content.getUrl(), e);
                }
            }
            if (!batch.hasNext()) break;
            page++;
        }
        if (page >= INDEXING_MAX_BATCHES) {
            logger.warn("Indexing stopped at batch cap for chatbot {} ({} items indexed)", chatbot.getId(), totalIndexed);
        }
        logger.info("Content indexing completed for chatbot: {} ({} pages)", chatbot.getId(), totalIndexed);
    }
    
    /**
     * Get conversation analytics
     */
    public Map<String, Object> getConversationAnalytics(Long chatbotId) {
        Chatbot chatbot = chatbotRepository.findById(chatbotId)
            .orElseThrow(() -> new RuntimeException("Chatbot not found"));
        
        List<Conversation> conversations = conversationRepository.findByChatbot(chatbot);
        
        Map<String, Object> analytics = new HashMap<>();
        analytics.put("totalConversations", conversations.size());
        analytics.put("activeConversations", conversations.stream().mapToInt(c -> c.getIsActive() ? 1 : 0).sum());
        analytics.put("totalMessages", conversations.stream().mapToInt(Conversation::getMessageCount).sum());
        
        // Average conversation duration
        double avgDuration = conversations.stream()
            .filter(c -> c.getEndedAt() != null)
            .mapToLong(Conversation::getDurationInMinutes)
            .average()
            .orElse(0.0);
        analytics.put("averageDurationMinutes", avgDuration);
        
        // Language distribution
        Map<String, Long> languageDistribution = conversations.stream()
            .filter(c -> c.getUserLanguage() != null)
            .collect(Collectors.groupingBy(
                Conversation::getUserLanguage,
                Collectors.counting()
            ));
        analytics.put("languageDistribution", languageDistribution);
        
        return analytics;
    }
}
