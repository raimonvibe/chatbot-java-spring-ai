package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.model.*;
import com.prayer_chat.chatbot.repository.BibleVerseRepository;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.repository.ConversationRepository;
import com.prayer_chat.chatbot.repository.MessageRepository;
import com.prayer_chat.chatbot.repository.WebsiteContentRepository;
import com.prayer_chat.chatbot.service.JesusTeachingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for AI-powered chatbot interactions using Spring AI
 */
@Service
@Transactional
public class AiChatbotService {
    
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

        // DEBUG: Log what we retrieved
        logger.info("🔍 DEBUG - Retrieved {} documents from vector store for chatbot {}",
            relevantDocs.size(), chatbot.getId());
        if (!relevantDocs.isEmpty()) {
            logger.info("🔍 DEBUG - First document preview: {}",
                relevantDocs.get(0).getText().substring(0, Math.min(200, relevantDocs.get(0).getText().length())));
        } else {
            logger.warn("⚠️ WARNING - No documents retrieved! Vector store may be empty for chatbot {}", chatbot.getId());
        }

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

        // DEBUG: Log the full system prompt
        logger.info("🔍 DEBUG - System prompt length: {} characters", systemPrompt.length());
        logger.info("🔍 DEBUG - System prompt preview (first 500 chars):\n{}",
            systemPrompt.substring(0, Math.min(500, systemPrompt.length())));
        if (systemPrompt.contains("WEBSITE INFORMATION:")) {
            int websiteInfoStart = systemPrompt.indexOf("WEBSITE INFORMATION:");
            int websiteInfoEnd = Math.min(websiteInfoStart + 800, systemPrompt.length());
            logger.info("🔍 DEBUG - Website information section:\n{}",
                systemPrompt.substring(websiteInfoStart, websiteInfoEnd));
        } else {
            logger.warn("⚠️ WARNING - System prompt does NOT contain 'WEBSITE INFORMATION:' section!");
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
    
    /**
     * Retrieve relevant context from vector store
     */
    private List<Document> retrieveRelevantContext(Chatbot chatbot, String userMessage) {
        try {
            // Build search request with metadata filtering for this specific chatbot
            SearchRequest searchRequest = SearchRequest.builder()
                .query(userMessage)
                .topK(20)  // Get more results before filtering
                .similarityThreshold(0.3)  // Minimum relevance threshold
                .filterExpression(String.format("chatbotId == '%s'", chatbot.getId().toString()))
                .build();

            // Search for relevant documents using Spring AI SearchRequest
            List<Document> documents = vectorStore.similaritySearch(searchRequest);

            // Log search results for debugging
            logger.info("Vector store search for chatbot '{}' (ID: {}) returned {} documents",
                chatbot.getName(), chatbot.getId(), documents.size());

            if (documents.isEmpty()) {
                logger.warn("No website content found in vector store for chatbot '{}'. " +
                    "Website content may not be indexed yet.", chatbot.getName());
            } else {
                logger.debug("Top document similarity scores: {}",
                    documents.stream()
                        .limit(3)
                        .map(doc -> String.format("%.3f", doc.getMetadata().get("distance")))
                        .collect(Collectors.joining(", ")));
            }

            // Return top 5 most relevant documents
            return documents.stream()
                .limit(5)
                .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Failed to retrieve context from vector store for chatbot: " + chatbot.getName(), e);
            return new ArrayList<>();
        }
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

            // Smart threshold logic
            if (isFirstMessage) {
                // First message: Use verse if similarity >= 0.5 (moderately relevant)
                if (bestSimilarity >= 0.5) {
                    logger.info("Found Bible verse for first message: {} (similarity: {:.2f})",
                        bestMatch.getReference(), String.format("%.2f", bestSimilarity));
                    return bestMatch;
                }
            } else {
                // Subsequent messages: Use verse only if highly or moderately relevant
                if (bestSimilarity >= 0.7) {
                    logger.info("Found highly relevant Bible verse: {} (similarity: {:.2f})",
                        bestMatch.getReference(), String.format("%.2f", bestSimilarity));
                    return bestMatch;
                } else if (bestSimilarity >= 0.5) {
                    logger.info("Found moderately relevant Bible verse: {} (similarity: {:.2f})",
                        bestMatch.getReference(), String.format("%.2f", bestSimilarity));
                    return bestMatch;
                }
            }

            logger.debug("No sufficiently relevant Bible verse found (best similarity: {:.2f})", String.format("%.2f", bestSimilarity));
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

        // Base system prompt with strict information usage guidelines
        prompt.append("You are an AI assistant for ").append(chatbot.getName()).append(".\n");
        prompt.append("You help visitors by answering questions about the business and its services.\n");
        prompt.append("Be helpful, friendly, and professional in your responses.\n\n");

        prompt.append("CRITICAL INSTRUCTIONS:\n");
        prompt.append("- ONLY use information from the 'WEBSITE INFORMATION' section below\n");
        prompt.append("- DO NOT make assumptions or use general knowledge about this business\n");
        prompt.append("- DO NOT invent services, products, or information not explicitly provided\n");
        prompt.append("- If the website information doesn't contain the answer, say: \"I don't have that specific information from the website. Please contact the business directly or visit their website for more details.\"\n");
        prompt.append("- Base ALL answers strictly on the provided website content\n\n");

        // Add Christian values if enabled
        if (chatbot.getChristianMessagingEnabled() != null && chatbot.getChristianMessagingEnabled()) {
            prompt.append("\n").append("=".repeat(60)).append("\n");
            prompt.append("🙏 CHRISTIAN MESSAGING APPROACH:\n");
            prompt.append("=".repeat(60)).append("\n");
            prompt.append("Your responses should reflect Christian values prominently:\n");
            prompt.append("- Approach ALL interactions with love, kindness, and compassion\n");
            prompt.append("- Reflect Christian values of honesty, integrity, and service to others\n");
            prompt.append("- Be respectful, patient, and understanding in all communications\n");
            prompt.append("- Weave Christian perspective naturally into your responses\n");
            prompt.append("- Seek to help, encourage, and bless those you interact with\n");
            prompt.append("- Show empathy and genuine care for the visitor's needs\n\n");

            // Add dynamically selected Bible verse (if found and relevant)
            if (relevantVerse != null) {
                prompt.append("📖 RELEVANT SCRIPTURE FOR THIS CONVERSATION:\n");
                prompt.append(relevantVerse.getReference()).append(" - \"").append(relevantVerse.getText()).append("\"\n\n");
                prompt.append("Scripture Usage Instructions:\n");
                if (isFirstMessage) {
                    prompt.append("- This is the FIRST message. Include this verse prominently in your greeting.\n");
                    prompt.append("- Explain how this verse relates to the business or the visitor's needs.\n");
                    prompt.append("- Make it conversational and welcoming, not preachy.\n");
                } else {
                    prompt.append("- Include this verse if it's relevant to the user's question.\n");
                    prompt.append("- Explain briefly how it connects to what they're asking about.\n");
                    prompt.append("- Make it feel natural and helpful, not forced.\n");
                }
                prompt.append("- Always cite the reference (e.g., 'As it says in ").append(relevantVerse.getReference()).append("...')\n\n");
            }

            // Add footer instruction for Christian blessing
            prompt.append("RESPONSE STRUCTURE REQUIREMENTS:\n");
            prompt.append("1. Start with warm, friendly greeting\n");
            prompt.append("2. Provide helpful information (based on website content)\n");
            if (relevantVerse != null) {
                prompt.append("3. Include the relevant Bible verse naturally in your response\n");
            }
            prompt.append("3. End with a meaningful Christian blessing or encouragement\n");
            prompt.append("   Examples: 'God bless you!', 'May you be blessed!', 'Grace and peace to you!', \n");
            prompt.append("   'May the Lord guide you!', 'Blessings on your journey!'\n");
            prompt.append("=".repeat(60)).append("\n");
        }

        // NEW: Add Jesus's teachings section if enabled
        if (jesusTeachingContext != null && !jesusTeachingContext.trim().isEmpty()) {
            prompt.append("\n").append("=".repeat(50)).append("\n");
            prompt.append("📖 WHAT JESUS WOULD SAY:\n");
            prompt.append(jesusTeachingContext).append("\n");
            prompt.append("=".repeat(50)).append("\n");

            prompt.append("\nInstructions for using Jesus's teachings:\n");
            prompt.append("- Draw inspiration from the teachings above\n");
            prompt.append("- Explain how Jesus's wisdom applies to this situation\n");
            prompt.append("- Use conversational language (not preachy)\n");
            prompt.append("- Connect the teachings to the specific question or context\n");
            prompt.append("- Be authentic and respectful\n");

            if (isFirstMessage) {
                prompt.append("- For the first message, include a brief 'Jesus's Perspective' on this business/website\n");
            } else {
                prompt.append("- Only include Jesus's perspective if it naturally fits the question\n");
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

        // Add relevant context from website
        prompt.append("\n").append("=".repeat(60)).append("\n");
        prompt.append("WEBSITE INFORMATION:\n");
        prompt.append("=".repeat(60)).append("\n");

        if (!relevantDocs.isEmpty()) {
            for (Document doc : relevantDocs) {
                prompt.append(doc.getText()).append("\n\n");
            }
            prompt.append("=".repeat(60)).append("\n");
        } else {
            prompt.append("⚠️ WARNING: No website content is currently available.\n");
            prompt.append("You must inform the user that you don't have information about this website yet.\n");
            prompt.append("Suggest they:\n");
            prompt.append("1. Check back later as the website is being analyzed\n");
            prompt.append("2. Visit the website directly\n");
            prompt.append("3. Contact the business for immediate assistance\n");
            prompt.append("DO NOT make up any information about the business.\n");
            prompt.append("=".repeat(60)).append("\n");
        }

        return prompt.toString();
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
    
    /**
     * Index website content for a chatbot
     */
    public void indexWebsiteContent(Chatbot chatbot) {
        logger.info("Starting content indexing for chatbot: {}", chatbot.getId());
        
        List<WebsiteContent> contents = websiteContentRepository.findByChatbot(chatbot);
        
        for (WebsiteContent content : contents) {
            try {
                // Create document for vector store
                Document document = new Document(
                    content.getContent(),
                    Map.of(
                        "chatbotId", chatbot.getId().toString(),
                        "url", content.getUrl(),
                        "title", content.getTitle(),
                        "language", content.getLanguage() != null ? content.getLanguage() : "en"
                    )
                );
                
                // Add to vector store
                vectorStore.add(List.of(document));
                
                // Mark as indexed
                content.setIsIndexed(true);
                content.setVectorId(document.getId());
                websiteContentRepository.save(content);
                
                logger.debug("Indexed content: {}", content.getUrl());
                
            } catch (Exception e) {
                logger.error("Failed to index content: {}", content.getUrl(), e);
            }
        }
        
        logger.info("Content indexing completed for chatbot: {}", chatbot.getId());
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
