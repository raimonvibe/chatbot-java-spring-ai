package com.noupe.chatbot.service;

import com.noupe.chatbot.model.*;
import com.noupe.chatbot.repository.ChatbotRepository;
import com.noupe.chatbot.repository.ConversationRepository;
import com.noupe.chatbot.repository.MessageRepository;
import com.noupe.chatbot.repository.WebsiteContentRepository;
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
    
    @Value("${app.chatbot.max-conversation-history:10}")
    private int maxConversationHistory;
    
    @Value("${app.chatbot.default-language:en}")
    private String defaultLanguage;
    
    @Autowired
    public AiChatbotService(ChatClient chatClient, VectorStore vectorStore, EmbeddingModel embeddingModel,
                           ChatbotRepository chatbotRepository, ConversationRepository conversationRepository,
                           MessageRepository messageRepository, WebsiteContentRepository websiteContentRepository) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.chatbotRepository = chatbotRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.websiteContentRepository = websiteContentRepository;
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
            com.noupe.chatbot.model.Message userMsg = new com.noupe.chatbot.model.Message(conversation, com.noupe.chatbot.model.Message.MessageType.TEXT, userMessage, true);
            messageRepository.save(userMsg);
            
            // Generate AI response
            String aiResponse = generateResponse(chatbot, conversation, userMessage, userLanguage);
            
            // Calculate response time
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Save AI response
            com.noupe.chatbot.model.Message aiMsg = new com.noupe.chatbot.model.Message(conversation, com.noupe.chatbot.model.Message.MessageType.TEXT, aiResponse, false);
            aiMsg.setResponseTimeMs((int) responseTime);
            messageRepository.save(aiMsg);
            
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
        
        if (conversation == null) {
            conversation = new Conversation(chatbot, sessionId);
            conversation.setUserLanguage(userLanguage != null ? userLanguage : defaultLanguage);
            conversation.setUserIp(userIp);
            conversation.setUserAgent(userAgent);
            conversationRepository.save(conversation);
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
        List<com.noupe.chatbot.model.Message> recentMessages = getRecentMessages(conversation);
        
        // Create system prompt with context
        String systemPrompt = buildSystemPrompt(chatbot, relevantDocs, userLanguage);
        
        // Build messages for the chat
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        
        // Add conversation history
        for (com.noupe.chatbot.model.Message msg : recentMessages) {
            if (msg.getIsUserMessage()) {
                messages.add(new UserMessage(msg.getContent()));
            } else {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }
        
        // Add current user message
        messages.add(new UserMessage(userMessage));
        
        // Generate response
        Prompt prompt = new Prompt(messages);
        ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
        
        return response.getResult().getOutput().getContent();
    }
    
    /**
     * Retrieve relevant context from vector store
     */
    private List<Document> retrieveRelevantContext(Chatbot chatbot, String userMessage) {
        try {
            // Search for relevant documents
            SearchRequest searchRequest = SearchRequest.query(userMessage)
                .withTopK(5)
                .withSimilarityThreshold(0.7);
            
            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            
            // Filter documents by chatbot
            return documents.stream()
                .filter(doc -> doc.getMetadata().containsKey("chatbotId") && 
                              doc.getMetadata().get("chatbotId").equals(chatbot.getId().toString()))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.warn("Failed to retrieve context from vector store", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get recent conversation history
     */
    private List<com.noupe.chatbot.model.Message> getRecentMessages(Conversation conversation) {
        return messageRepository.findByConversationOrderByCreatedAtDesc(conversation)
            .stream()
            .limit(maxConversationHistory)
            .sorted(Comparator.comparing(com.noupe.chatbot.model.Message::getCreatedAt))
            .collect(Collectors.toList());
    }
    
    /**
     * Build system prompt with context
     */
    private String buildSystemPrompt(Chatbot chatbot, List<Document> relevantDocs, String userLanguage) {
        StringBuilder prompt = new StringBuilder();
        
        // Base system prompt
        prompt.append("You are an AI assistant for ").append(chatbot.getName()).append(".\n");
        prompt.append("You help visitors by answering questions about the business and its services.\n");
        prompt.append("Be helpful, friendly, and professional in your responses.\n");
        prompt.append("If you don't know something, politely say so and suggest contacting the business directly.\n");
        
        // Add custom prompt if configured
        if (chatbot.getCustomPrompt() != null && !chatbot.getCustomPrompt().trim().isEmpty()) {
            prompt.append("\nAdditional instructions: ").append(chatbot.getCustomPrompt()).append("\n");
        }
        
        // Add language-specific instructions
        if (userLanguage != null && !userLanguage.equals("en")) {
            prompt.append("\nRespond in ").append(getLanguageName(userLanguage)).append(".\n");
        }
        
        // Add relevant context
        if (!relevantDocs.isEmpty()) {
            prompt.append("\nRelevant information about the business:\n");
            for (Document doc : relevantDocs) {
                prompt.append("- ").append(doc.getContent()).append("\n");
            }
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
