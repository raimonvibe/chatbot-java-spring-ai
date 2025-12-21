package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.dto.ChatbotRequest;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
import com.prayer_chat.chatbot.service.AiChatbotService;
import com.prayer_chat.chatbot.service.ChatbotService;
import com.prayer_chat.chatbot.service.WebsiteAnalysisService;
import com.prayer_chat.chatbot.service.ConversationExportService;
import com.prayer_chat.chatbot.service.BibleVerseService;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.util.LogSanitizer;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for chatbot management
 */
@RestController
@RequestMapping("/api/chatbots")
public class ChatbotController {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatbotController.class);
    
    private final ChatbotRepository chatbotRepository;
    private final ChatbotService chatbotService;
    private final AiChatbotService aiChatbotService;
    private final WebsiteAnalysisService websiteAnalysisService;
    private final ConversationExportService conversationExportService;
    private final BibleVerseService bibleVerseService;
    private final SubscriptionRepository subscriptionRepository;

    @Autowired
    public ChatbotController(ChatbotRepository chatbotRepository,
                           ChatbotService chatbotService,
                           AiChatbotService aiChatbotService,
                           WebsiteAnalysisService websiteAnalysisService,
                           ConversationExportService conversationExportService,
                           BibleVerseService bibleVerseService,
                           SubscriptionRepository subscriptionRepository) {
        this.chatbotRepository = chatbotRepository;
        this.chatbotService = chatbotService;
        this.aiChatbotService = aiChatbotService;
        this.websiteAnalysisService = websiteAnalysisService;
        this.conversationExportService = conversationExportService;
        this.bibleVerseService = bibleVerseService;
        this.subscriptionRepository = subscriptionRepository;
    }

    // ============================================================================
    // HELPER METHODS FOR AUTHORIZATION AND SUBSCRIPTION
    // ============================================================================

    /**
     * Check if user has an active subscription
     */
    private boolean hasActiveSubscription(User user) {
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByUserId(user.getId());
        return subscriptionOpt.isPresent() && subscriptionOpt.get().canUseChatbot();
    }

    /**
     * Check if user owns the chatbot
     */
    private boolean isOwner(User user, Chatbot chatbot) {
        return chatbot.getOwner() != null && chatbot.getOwner().getId().equals(user.getId());
    }

    /**
     * Verify user can access chatbot (owns it and has active subscription)
     */
    private ResponseEntity<Void> verifyAccess(User user, Chatbot chatbot) {
        if (!hasActiveSubscription(user)) {
            logger.warn("User {} attempted to access chatbot without active subscription", LogSanitizer.sanitize(user.getEmail()));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!isOwner(user, chatbot)) {
            logger.warn("User {} attempted to access chatbot {} without ownership", LogSanitizer.sanitize(user.getEmail()), chatbot.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return null;
    }
    
    /**
     * Get all chatbots owned by the current user
     */
    @GetMapping
    public ResponseEntity<List<Chatbot>> getAllChatbots(
            @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            // If user is not authenticated, return empty list
            if (currentUser == null) {
                logger.debug("Unauthenticated user attempted to access chatbots");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            User user = currentUser.getUser();

            if (!hasActiveSubscription(user)) {
                logger.warn("User {} attempted to access chatbots without active subscription", LogSanitizer.sanitize(user.getEmail()));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            List<Chatbot> chatbots = chatbotRepository.findAll().stream()
                .filter(chatbot -> isOwner(user, chatbot))
                .toList();

            return ResponseEntity.ok(chatbots);
        } catch (Exception e) {
            logger.error("Error retrieving chatbots", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Search chatbots by query
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchChatbots(
            @RequestParam String query,
            @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            // Validate query parameter for NoSQL injection attempts
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Query parameter is required"));
            }

            // Reject NoSQL injection patterns
            if (query.contains("{") || query.contains("}") || query.contains("$") ||
                query.contains("[") || query.contains("]")) {
                logger.warn("Rejected search query with potential NoSQL injection: {}", LogSanitizer.sanitize(query));
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid query format"));
            }

            // Check if user is authenticated (for testing, may be null)
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            User user = currentUser.getUser();
            if (!hasActiveSubscription(user)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Simple search implementation (can be enhanced)
            List<Chatbot> results = chatbotRepository.findAll().stream()
                .filter(chatbot -> isOwner(user, chatbot))
                .filter(chatbot -> chatbot.getName().toLowerCase().contains(query.toLowerCase()) ||
                                   (chatbot.getDescription() != null &&
                                    chatbot.getDescription().toLowerCase().contains(query.toLowerCase())))
                .toList();

            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error searching chatbots", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get chatbot by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getChatbot(@PathVariable Long id,
                                              @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            // Validate ID is positive
            if (id == null || id < 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid chatbot ID"));
            }

            // Check if user is authenticated (for testing, may be null)
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            User user = currentUser.getUser();
            Optional<Chatbot> chatbotOpt = chatbotRepository.findById(id);

            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Chatbot chatbot = chatbotOpt.get();
            ResponseEntity<Void> accessCheck = verifyAccess(user, chatbot);
            if (accessCheck != null) {
                return ResponseEntity.status(accessCheck.getStatusCode()).build();
            }

            return ResponseEntity.ok(chatbot);
        } catch (Exception e) {
            logger.error("Error retrieving chatbot {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Create a new chatbot
     */
    @PostMapping
    public ResponseEntity<?> createChatbot(@Valid @RequestBody ChatbotRequest request,
                                                 @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            // Validation happens automatically via @Valid - if we reach here, input is valid
            // Check if user is authenticated (for testing, may be null)
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            User user = currentUser.getUser();

            // Check if user has active subscription
            if (!hasActiveSubscription(user)) {
                logger.warn("User {} attempted to create chatbot without active subscription", LogSanitizer.sanitize(user.getEmail()));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Convert DTO to entity
            Chatbot chatbot = new Chatbot();
            chatbot.setName(request.getName());
            chatbot.setWebsiteUrl(request.getWebsiteUrl());
            chatbot.setDescription(request.getDescription());
            chatbot.setPrimaryLanguage(request.getPrimaryLanguage());

            // Parse supported languages from comma-separated string
            if (request.getSupportedLanguages() != null && !request.getSupportedLanguages().trim().isEmpty()) {
                chatbot.setSupportedLanguages(
                    java.util.Arrays.asList(request.getSupportedLanguages().split(","))
                        .stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(java.util.stream.Collectors.toList())
                );
            }

            chatbot.setCustomPrompt(request.getCustomPrompt());
            chatbot.setWebhookUrl(request.getWebhookUrl());
            chatbot.setBrandingConfig(request.getBrandingConfig());
            chatbot.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

            // Generate unique embed code
            chatbot.setEmbedCode(String.format("prayer-chat-bot-%d", System.currentTimeMillis()));

            // Use ChatbotService for secure creation with validation, sanitization, and audit logging
            Chatbot savedChatbot = chatbotService.createChatbot(chatbot, user);
            logger.info("Created new chatbot: {} for user: {}", LogSanitizer.sanitize(savedChatbot.getName()), LogSanitizer.sanitize(user.getEmail()));
            return ResponseEntity.status(HttpStatus.CREATED).body(savedChatbot);
        } catch (Exception e) {
            logger.error("Error creating chatbot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Update a chatbot
     */
    @PutMapping("/{id}")
    public ResponseEntity<Chatbot> updateChatbot(@PathVariable Long id,
                                                 @Valid @RequestBody Chatbot chatbotDetails,
                                                 @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            User user = currentUser.getUser();
            Optional<Chatbot> chatbotOpt = chatbotRepository.findById(id);

            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Chatbot chatbot = chatbotOpt.get();

            // Verify access
            ResponseEntity<Void> accessCheck = verifyAccess(user, chatbot);
            if (accessCheck != null) {
                return ResponseEntity.status(accessCheck.getStatusCode()).build();
            }
            chatbot.setName(chatbotDetails.getName());
            chatbot.setDescription(chatbotDetails.getDescription());
            chatbot.setPrimaryLanguage(chatbotDetails.getPrimaryLanguage());
            chatbot.setSupportedLanguages(chatbotDetails.getSupportedLanguages());
            chatbot.setCustomPrompt(chatbotDetails.getCustomPrompt());
            chatbot.setBrandingConfig(chatbotDetails.getBrandingConfig());
            chatbot.setIsActive(chatbotDetails.getIsActive());
            // NEW FEATURES
            chatbot.setWebhookUrl(chatbotDetails.getWebhookUrl());
            chatbot.setWebhookEvents(chatbotDetails.getWebhookEvents());
            chatbot.setQuickReplies(chatbotDetails.getQuickReplies());
            // CHRISTIAN MESSAGING FEATURES
            chatbot.setBibleVerse(chatbotDetails.getBibleVerse());
            chatbot.setChristianMessagingEnabled(chatbotDetails.getChristianMessagingEnabled());

            Chatbot updatedChatbot = chatbotRepository.save(chatbot);
            logger.info("Updated chatbot: {}", LogSanitizer.sanitize(updatedChatbot.getName()));
            return ResponseEntity.ok(updatedChatbot);
            
        } catch (Exception e) {
            logger.error("Error updating chatbot {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Delete a chatbot
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChatbot(@PathVariable Long id,
                                              @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            User user = currentUser.getUser();
            Optional<Chatbot> chatbotOpt = chatbotRepository.findById(id);

            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Chatbot chatbot = chatbotOpt.get();

            // Verify access
            ResponseEntity<Void> accessCheck = verifyAccess(user, chatbot);
            if (accessCheck != null) {
                return accessCheck;
            }

            chatbotRepository.deleteById(id);
            logger.info("Deleted chatbot: {} for user: {}", id, LogSanitizer.sanitize(user.getEmail()));
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            logger.error("Error deleting chatbot {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Analyze website for a chatbot
     */
    @PostMapping("/{id}/analyze")
    public ResponseEntity<Map<String, Object>> analyzeWebsite(@PathVariable Long id,
                                                              @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            User user = currentUser.getUser();
            Optional<Chatbot> chatbotOpt = chatbotRepository.findById(id);

            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Chatbot chatbot = chatbotOpt.get();

            // Verify access
            ResponseEntity<Void> accessCheck = verifyAccess(user, chatbot);
            if (accessCheck != null) {
                return ResponseEntity.status(accessCheck.getStatusCode()).build();
            }
            
            // Start website analysis asynchronously
            CompletableFuture<List<com.prayer_chat.chatbot.model.WebsiteContent>> analysisFuture = 
                websiteAnalysisService.analyzeWebsite(chatbot);
            
            // Return analysis status
            Map<String, Object> response = Map.of(
                "status", "analysis_started",
                "chatbotId", id,
                "websiteUrl", chatbot.getWebsiteUrl(),
                "message", "Website analysis started. Check back later for results."
            );
            
            logger.info("Started website analysis for chatbot: {}", LogSanitizer.sanitize(chatbot.getName()));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error starting website analysis for chatbot {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Index website content for a chatbot
     */
    @PostMapping("/{id}/index")
    public ResponseEntity<Map<String, Object>> indexContent(@PathVariable Long id,
                                                            @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            User user = currentUser.getUser();
            Optional<Chatbot> chatbotOpt = chatbotRepository.findById(id);

            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Chatbot chatbot = chatbotOpt.get();

            // Verify access
            ResponseEntity<Void> accessCheck = verifyAccess(user, chatbot);
            if (accessCheck != null) {
                return ResponseEntity.status(accessCheck.getStatusCode()).build();
            }
            
            // Start content indexing
            aiChatbotService.indexWebsiteContent(chatbot);
            
            Map<String, Object> response = Map.of(
                "status", "indexing_completed",
                "chatbotId", id,
                "message", "Website content has been indexed and is ready for chatbot interactions."
            );
            
            logger.info("Completed content indexing for chatbot: {}", LogSanitizer.sanitize(chatbot.getName()));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error indexing content for chatbot {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get chatbot analytics
     */
    @GetMapping("/{id}/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics(@PathVariable Long id,
                                                            @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            User user = currentUser.getUser();
            Optional<Chatbot> chatbotOpt = chatbotRepository.findById(id);

            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Chatbot chatbot = chatbotOpt.get();

            // Verify access
            ResponseEntity<Void> accessCheck = verifyAccess(user, chatbot);
            if (accessCheck != null) {
                return ResponseEntity.status(accessCheck.getStatusCode()).build();
            }
            
            // Get conversation analytics
            Map<String, Object> conversationAnalytics = aiChatbotService.getConversationAnalytics(id);
            
            // Get website analysis stats
            Map<String, Object> analysisStats = websiteAnalysisService.getAnalysisStats(chatbot);
            
            // Combine analytics
            Map<String, Object> analytics = Map.of(
                "chatbotId", id,
                "chatbotName", chatbot.getName(),
                "conversations", conversationAnalytics,
                "websiteAnalysis", analysisStats,
                "status", chatbot.getIsActive() ? "active" : "inactive"
            );
            
            return ResponseEntity.ok(analytics);
            
        } catch (Exception e) {
            logger.error("Error retrieving analytics for chatbot {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get chatbot embed code
     */
    @GetMapping("/{id}/embed")
    public ResponseEntity<Map<String, String>> getEmbedCode(@PathVariable Long id,
                                                            @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            User user = currentUser.getUser();
            Optional<Chatbot> chatbotOpt = chatbotRepository.findById(id);

            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Chatbot chatbot = chatbotOpt.get();

            // Verify access
            ResponseEntity<Void> accessCheck = verifyAccess(user, chatbot);
            if (accessCheck != null) {
                return ResponseEntity.status(accessCheck.getStatusCode()).build();
            }
            String embedCode = generateEmbedCode(chatbot);
            
            Map<String, String> response = Map.of(
                "embedCode", embedCode,
                "chatbotId", chatbot.getId().toString(),
                "chatbotName", chatbot.getName()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error generating embed code for chatbot {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Generate embed code for chatbot
     */
    private String generateEmbedCode(Chatbot chatbot) {
        return String.format("""
            <div id="prayer-chat-chatbot-%d" data-chatbot-id="%d"></div>
            <script>
                (function() {
                    var script = document.createElement('script');
                    script.src = 'http://localhost:8080/js/chatbot-widget.js';
                    script.async = true;
                    script.onload = function() {
                        PrayerChat.init({
                            chatbotId: %d,
                            apiUrl: 'http://localhost:8080/api',
                            theme: 'default'
                        });
                    };
                    document.head.appendChild(script);
                })();
            </script>
            """, chatbot.getId(), chatbot.getId(), chatbot.getId());
    }

    // ============================================================================
    // NEW FEATURE ENDPOINTS
    // ============================================================================

    /**
     * NEW FEATURE: Export conversation to JSON
     */
    @GetMapping("/conversations/{conversationId}/export/json")
    public ResponseEntity<String> exportConversationJson(@PathVariable Long conversationId) {
        try {
            String jsonExport = conversationExportService.exportConversationToJson(conversationId);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .header("Content-Disposition", "attachment; filename=conversation-" + conversationId + ".json")
                    .body(jsonExport);
        } catch (Exception e) {
            logger.error("Error exporting conversation {} to JSON", conversationId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * NEW FEATURE: Export conversation to CSV
     */
    @GetMapping("/conversations/{conversationId}/export/csv")
    public ResponseEntity<String> exportConversationCsv(@PathVariable Long conversationId) {
        try {
            String csvExport = conversationExportService.exportConversationToCsv(conversationId);
            return ResponseEntity.ok()
                    .header("Content-Type", "text/csv")
                    .header("Content-Disposition", "attachment; filename=conversation-" + conversationId + ".csv")
                    .body(csvExport);
        } catch (Exception e) {
            logger.error("Error exporting conversation {} to CSV", conversationId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * NEW FEATURE: Export all conversations for a chatbot to JSON
     */
    @GetMapping("/{id}/export/json")
    public ResponseEntity<String> exportChatbotConversationsJson(@PathVariable Long id,
                                                                  @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            User user = currentUser.getUser();
            Optional<Chatbot> chatbotOpt = chatbotRepository.findById(id);

            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Chatbot chatbot = chatbotOpt.get();

            // Verify access
            ResponseEntity<Void> accessCheck = verifyAccess(user, chatbot);
            if (accessCheck != null) {
                return ResponseEntity.status(accessCheck.getStatusCode()).build();
            }

            String jsonExport = conversationExportService.exportConversationsToJson(id);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .header("Content-Disposition", "attachment; filename=chatbot-" + id + "-conversations.json")
                    .body(jsonExport);
        } catch (Exception e) {
            logger.error("Error exporting chatbot {} conversations to JSON", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * NEW FEATURE: Export all conversations for a chatbot to CSV
     */
    @GetMapping("/{id}/export/csv")
    public ResponseEntity<String> exportChatbotConversationsCsv(@PathVariable Long id,
                                                                 @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            User user = currentUser.getUser();
            Optional<Chatbot> chatbotOpt = chatbotRepository.findById(id);

            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Chatbot chatbot = chatbotOpt.get();

            // Verify access
            ResponseEntity<Void> accessCheck = verifyAccess(user, chatbot);
            if (accessCheck != null) {
                return ResponseEntity.status(accessCheck.getStatusCode()).build();
            }

            String csvExport = conversationExportService.exportConversationsToCsv(id);
            return ResponseEntity.ok()
                    .header("Content-Type", "text/csv")
                    .header("Content-Disposition", "attachment; filename=chatbot-" + id + "-conversations.csv")
                    .body(csvExport);
        } catch (Exception e) {
            logger.error("Error exporting chatbot {} conversations to CSV", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * NEW FEATURE: Get quick replies for a chatbot
     */
    @GetMapping("/{id}/quick-replies")
    public ResponseEntity<String> getQuickReplies(@PathVariable Long id,
                                                  @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            User user = currentUser.getUser();
            Optional<Chatbot> chatbotOpt = chatbotRepository.findById(id);

            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Chatbot chatbot = chatbotOpt.get();

            // Verify access
            ResponseEntity<Void> accessCheck = verifyAccess(user, chatbot);
            if (accessCheck != null) {
                return ResponseEntity.status(accessCheck.getStatusCode()).build();
            }

            String quickReplies = chatbot.getQuickReplies();
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(quickReplies != null ? quickReplies : "[]");
        } catch (Exception e) {
            logger.error("Error retrieving quick replies for chatbot {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * CHRISTIAN MESSAGING FEATURE: Suggest Bible verse based on website topic
     */
    @PostMapping("/{id}/suggest-bible-verse")
    public ResponseEntity<Map<String, String>> suggestBibleVerse(@PathVariable Long id,
                                                                 @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            User user = currentUser.getUser();
            Optional<Chatbot> chatbotOpt = chatbotRepository.findById(id);

            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Chatbot chatbot = chatbotOpt.get();

            // Verify access
            ResponseEntity<Void> accessCheck = verifyAccess(user, chatbot);
            if (accessCheck != null) {
                return ResponseEntity.status(accessCheck.getStatusCode()).build();
            }

            // Gather website content for context
            String websiteContent = websiteAnalysisService.getAnalyzedContent(chatbot);

            // Suggest Bible verse
            String suggestedVerse = bibleVerseService.suggestBibleVerse(
                chatbot.getWebsiteUrl(),
                chatbot.getDescription(),
                websiteContent
            );

            // Auto-update chatbot with suggested verse if Christian messaging is enabled
            if (chatbot.getChristianMessagingEnabled() != null && chatbot.getChristianMessagingEnabled()) {
                chatbot.setBibleVerse(suggestedVerse);
                chatbotRepository.save(chatbot);
            }

            Map<String, String> response = Map.of(
                "chatbotId", id.toString(),
                "suggestedVerse", suggestedVerse,
                "autoApplied", (chatbot.getChristianMessagingEnabled() != null && chatbot.getChristianMessagingEnabled()) ? "true" : "false"
            );

            logger.info("Suggested Bible verse for chatbot {}: {}", id, suggestedVerse);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error suggesting Bible verse for chatbot {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
