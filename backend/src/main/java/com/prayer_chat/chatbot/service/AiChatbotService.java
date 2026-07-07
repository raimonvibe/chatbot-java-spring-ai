package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.dto.ChatRequestContext;
import com.prayer_chat.chatbot.exception.ResourceNotFoundException;
import com.prayer_chat.chatbot.model.*;
import com.prayer_chat.chatbot.repository.BibleVerseRepository;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.repository.ConversationRepository;
import com.prayer_chat.chatbot.repository.MessageRepository;
import com.prayer_chat.chatbot.repository.WebsiteContentRepository;
import com.prayer_chat.chatbot.util.LogSanitizer;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

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
    private static final String RELATED_SCRIPTURE_PREFIX = "Related Scripture:";
    /** Per-document cap for RAG context (matches the DB-snapshot truncation used elsewhere). */
    private static final int RAG_DOC_MAX_CHARS = 3500;
    /** Total cap for the retrieved-content block in the system prompt. */
    private static final int RAG_CONTEXT_MAX_CHARS = 24_000;
    private static final int VERSE_CADENCE_RESPONSES = 3;
    private static final int VERSE_COOLDOWN_RECENT_AI_MESSAGES = 2;
    private static final int VERSE_EXCERPT_MAX_CHARS = 180;
    
    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final ChatbotRepository chatbotRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final WebsiteContentRepository websiteContentRepository;
    private final BibleVerseRepository bibleVerseRepository;
    private final WebhookService webhookService;
    private final JesusTeachingsService jesusTeachingsService;
    private final RagContextRetrievalService ragContextRetrievalService;
    private final RagPromptBuilder ragPromptBuilder;

    @Value("${app.chatbot.max-conversation-history:10}")
    private int maxConversationHistory;

    @Value("${app.chatbot.default-language:en}")
    private String defaultLanguage;

    @Value("${app.rag-observability.enabled:true}")
    private boolean ragObservabilityEnabled;

    @Value("${app.rag-observability.max-docs-logged:8}")
    private int ragObservabilityMaxDocsLogged;

    @Value("${app.rag-observability.max-snippet-chars:220}")
    private int ragObservabilityMaxSnippetChars;

    public AiChatbotService(ChatClient chatClient, VectorStore vectorStore, EmbeddingModel embeddingModel,
                           ChatbotRepository chatbotRepository, ConversationRepository conversationRepository,
                           MessageRepository messageRepository, WebsiteContentRepository websiteContentRepository,
                           BibleVerseRepository bibleVerseRepository, WebhookService webhookService,
                           JesusTeachingsService jesusTeachingsService,
                           RagContextRetrievalService ragContextRetrievalService,
                           RagPromptBuilder ragPromptBuilder) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.chatbotRepository = chatbotRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.websiteContentRepository = websiteContentRepository;
        this.bibleVerseRepository = bibleVerseRepository;
        this.webhookService = webhookService;
        this.jesusTeachingsService = jesusTeachingsService;
        this.ragContextRetrievalService = ragContextRetrievalService;
        this.ragPromptBuilder = ragPromptBuilder;
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
     * Process a user message and generate a response.
     *
     * <p>Runs WITHOUT a wrapping transaction (overriding the class-level @Transactional):
     * the LLM call can take seconds and must not hold a DB connection for its duration
     * (pool exhaustion under load). Each repository operation commits in its own short
     * transaction, which also means messages are committed before webhooks fire — no
     * phantom webhook events for rolled-back messages. The flow touches no lazy
     * associations, so detached entities are safe here.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public ChatResponse processMessage(Long chatbotId, String userMessage, String sessionId,
                                     String userLanguage, String userIp, String userAgent) {
        return processMessage(new ChatRequestContext(chatbotId, userMessage, sessionId, userLanguage, userIp, userAgent));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public ChatResponse processMessage(ChatRequestContext context) {
        Long chatbotId = context.chatbotId();
        String userMessage = context.userMessage();
        String sessionId = context.sessionId();
        String userLanguage = context.userLanguage();
        String userIp = context.userIp();
        String userAgent = context.userAgent();
        
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        
        try {
            // Get chatbot
            Chatbot chatbot = chatbotRepository.findById(chatbotId)
                .orElseThrow(() -> ResourceNotFoundException.forResource("Chatbot", chatbotId));

            if (!Boolean.TRUE.equals(chatbot.getIsActive())) {
                throw new IllegalArgumentException("Chatbot is not active");
            }
            
            // Get or create conversation
            Conversation conversation = getOrCreateConversation(chatbot, sessionId, userLanguage, userIp, userAgent);
            
            // Save user message
            com.prayer_chat.chatbot.model.Message userMsg = new com.prayer_chat.chatbot.model.Message(conversation, com.prayer_chat.chatbot.model.Message.MessageType.TEXT, userMessage, true);
            messageRepository.save(userMsg);

            // NEW FEATURE: Send webhook event for new message
            webhookService.sendNewMessageEvent(chatbot, conversation, userMsg);

            if (ragObservabilityEnabled) {
                logger.info(
                    "RAG_OBS query trace={} chatbotId={} sessionId={} lang={} message={}",
                    traceId,
                    chatbotId,
                    LogSanitizer.sanitizeForLogging(sessionId),
                    userLanguage,
                    LogSanitizer.sanitizeForLogging(userMessage)
                );
            }

            // Generate AI response
            String aiResponse = generateResponse(chatbot, conversation, userMessage, userLanguage, traceId);

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
            try {
                conversation = conversationRepository.save(conversation);
                webhookService.sendNewConversationEvent(chatbot, conversation);
            } catch (DataIntegrityViolationException ex) {
                conversation = conversationRepository.findByChatbotAndSessionId(chatbot, sessionId)
                    .orElseThrow(() -> ex);
            }
        }

        return conversation;
    }
    
    /**
     * Generate AI response using RAG (Retrieval Augmented Generation)
     */
    private String generateResponse(Chatbot chatbot, Conversation conversation, String userMessage, String userLanguage, String traceId) {

        // Retrieve relevant context from vector store
        List<Document> relevantDocs = ragContextRetrievalService.retrieveRelevantContext(chatbot, userMessage);

        if (ragObservabilityEnabled) {
            ragContextRetrievalService.logRetrievedContext(traceId, chatbot, relevantDocs);
        }

        // Build conversation history
        List<com.prayer_chat.chatbot.model.Message> recentMessages = getRecentMessages(conversation);

        // First turn: user message was saved before this call; no assistant replies exist yet.
        boolean isFirstMessage = recentMessages.stream().noneMatch(m -> !Boolean.TRUE.equals(m.getIsUserMessage()));

        // Find relevant Bible verse if Christian messaging is enabled
        BibleVerse relevantVerse = null;
        if (chatbot.getChristianMessagingEnabled() != null && chatbot.getChristianMessagingEnabled()) {
            relevantVerse = ragContextRetrievalService.findRelevantBibleVerse(userMessage, relevantDocs, isFirstMessage);
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
        String systemPrompt = ragPromptBuilder.buildSystemPrompt(
            chatbot, relevantDocs, userLanguage, relevantVerse, jesusTeachingContext, isFirstMessage);

        // Add Christian greeting instruction for first message
        if (isFirstMessage && chatbot.getChristianMessagingEnabled() != null && chatbot.getChristianMessagingEnabled()) {
            systemPrompt += "\nIMPORTANT: This is the first message. Start your response with a warm Christian greeting (e.g., 'Welcome! God's blessings to you!', 'Greetings in Christ!', 'Peace be with you!').\n";
        }

        // Build messages for the chat
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));

        // The current user message was already saved before this call, so it is the last
        // entry of recentMessages — exclude it from history to avoid sending it twice.
        List<com.prayer_chat.chatbot.model.Message> history = new ArrayList<>(recentMessages);
        if (!history.isEmpty()) {
            com.prayer_chat.chatbot.model.Message last = history.get(history.size() - 1);
            if (Boolean.TRUE.equals(last.getIsUserMessage()) && Objects.equals(last.getContent(), userMessage)) {
                history.remove(history.size() - 1);
            }
        }

        // Add conversation history
        for (com.prayer_chat.chatbot.model.Message msg : history) {
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
            String aiText = response.getResult().getOutput().getText();
            if (shouldAppendRelatedScripture(chatbot, conversation, recentMessages, relevantVerse, isFirstMessage)) {
                aiText = appendRelatedScripture(aiText, relevantVerse);
            }
            if (ragObservabilityEnabled) {
                ragContextRetrievalService.logAnswerGrounding(traceId, relevantDocs, aiText);
            }
            return aiText;
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
     * Option C policy: include scripture on first turn when relevant, then occasionally with cadence + cooldown.
     */
    private boolean shouldAppendRelatedScripture(Chatbot chatbot,
                                                 Conversation conversation,
                                                 List<com.prayer_chat.chatbot.model.Message> recentMessages,
                                                 BibleVerse relevantVerse,
                                                 boolean isFirstMessage) {
        if (chatbot == null || conversation == null || relevantVerse == null) return false;
        if (chatbot.getChristianMessagingEnabled() == null || !chatbot.getChristianMessagingEnabled()) return false;
        if (isFirstMessage) return true;

        // Cooldown: avoid scripture in consecutive responses.
        long recentAiWithScripture = recentMessages.stream()
            .filter(m -> m != null && Boolean.FALSE.equals(m.getIsUserMessage()))
            .limit(VERSE_COOLDOWN_RECENT_AI_MESSAGES)
            .map(com.prayer_chat.chatbot.model.Message::getContent)
            .filter(Objects::nonNull)
            .filter(t -> t.contains(RELATED_SCRIPTURE_PREFIX))
            .count();
        if (recentAiWithScripture > 0) return false;

        // Cadence by assistant response number (3rd, 6th, 9th, ...), if relevant verse exists.
        long priorAiCount = Optional.ofNullable(messageRepository.countAiMessagesByConversation(conversation)).orElse(0L);
        long nextAiIndex = priorAiCount + 1L;
        return nextAiIndex % VERSE_CADENCE_RESPONSES == 0;
    }

    private String appendRelatedScripture(String aiResponse, BibleVerse verse) {
        if (verse == null) return aiResponse;
        String response = aiResponse == null ? "" : aiResponse.trim();
        String reference = verse.getReference() != null ? verse.getReference().trim() : "";
        String verseText = verse.getText() != null ? verse.getText().replaceAll("\\s+", " ").trim() : "";
        if (verseText.length() > VERSE_EXCERPT_MAX_CHARS) {
            verseText = verseText.substring(0, VERSE_EXCERPT_MAX_CHARS).trim() + "...";
        }
        // If the model already cited this exact reference, avoid duplicate scripture blocks.
        if (!reference.isEmpty() && response.toLowerCase(Locale.ROOT).contains(reference.toLowerCase(Locale.ROOT))) {
            return response;
        }
        String line = (reference.isEmpty() ? RELATED_SCRIPTURE_PREFIX : (RELATED_SCRIPTURE_PREFIX + " " + reference))
            + (verseText.isEmpty() ? "" : " — \"" + verseText + "\"");
        return response.isEmpty() ? line : (response + "\n\n" + line);
    }
    
    private List<com.prayer_chat.chatbot.model.Message> getRecentMessages(Conversation conversation) {
        return messageRepository.findByConversationOrderByCreatedAtDesc(conversation)
            .stream()
            .limit(maxConversationHistory)
            .sorted(Comparator.comparing(com.prayer_chat.chatbot.model.Message::getCreatedAt))
            .collect(Collectors.toList());
    }


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


    static String safeUrlForPrompt(String url) {
        return RagPromptBuilder.safeUrlForPrompt(url);
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
