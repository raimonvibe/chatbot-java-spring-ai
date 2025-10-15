package com.noupe.chatbot.controller;

import com.noupe.chatbot.model.Chatbot;
import com.noupe.chatbot.service.AiChatbotService;
import com.noupe.chatbot.service.WebsiteAnalysisService;
import com.noupe.chatbot.repository.ChatbotRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@CrossOrigin(origins = "*")
public class ChatbotController {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatbotController.class);
    
    private final ChatbotRepository chatbotRepository;
    private final AiChatbotService aiChatbotService;
    private final WebsiteAnalysisService websiteAnalysisService;
    
    @Autowired
    public ChatbotController(ChatbotRepository chatbotRepository, 
                           AiChatbotService aiChatbotService,
                           WebsiteAnalysisService websiteAnalysisService) {
        this.chatbotRepository = chatbotRepository;
        this.aiChatbotService = aiChatbotService;
        this.websiteAnalysisService = websiteAnalysisService;
    }
    
    /**
     * Get all chatbots
     */
    @GetMapping
    public ResponseEntity<List<Chatbot>> getAllChatbots() {
        try {
            List<Chatbot> chatbots = chatbotRepository.findAll();
            return ResponseEntity.ok(chatbots);
        } catch (Exception e) {
            logger.error("Error retrieving chatbots", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get chatbot by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Chatbot> getChatbot(@PathVariable Long id) {
        try {
            Optional<Chatbot> chatbot = chatbotRepository.findById(id);
            return chatbot.map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            logger.error("Error retrieving chatbot {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Create a new chatbot
     */
    @PostMapping
    public ResponseEntity<Chatbot> createChatbot(@Valid @RequestBody Chatbot chatbot) {
        try {
            Chatbot savedChatbot = chatbotRepository.save(chatbot);
            logger.info("Created new chatbot: {}", savedChatbot.getName());
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
    public ResponseEntity<Chatbot> updateChatbot(@PathVariable Long id, @Valid @RequestBody Chatbot chatbotDetails) {
        try {
            Optional<Chatbot> chatbotOpt = chatbotRepository.findById(id);
            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Chatbot chatbot = chatbotOpt.get();
            chatbot.setName(chatbotDetails.getName());
            chatbot.setDescription(chatbotDetails.getDescription());
            chatbot.setPrimaryLanguage(chatbotDetails.getPrimaryLanguage());
            chatbot.setSupportedLanguages(chatbotDetails.getSupportedLanguages());
            chatbot.setCustomPrompt(chatbotDetails.getCustomPrompt());
            chatbot.setBrandingConfig(chatbotDetails.getBrandingConfig());
            chatbot.setIsActive(chatbotDetails.getIsActive());
            
            Chatbot updatedChatbot = chatbotRepository.save(chatbot);
            logger.info("Updated chatbot: {}", updatedChatbot.getName());
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
    public ResponseEntity<Void> deleteChatbot(@PathVariable Long id) {
        try {
            if (!chatbotRepository.existsById(id)) {
                return ResponseEntity.notFound().build();
            }
            
            chatbotRepository.deleteById(id);
            logger.info("Deleted chatbot: {}", id);
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
    public ResponseEntity<Map<String, Object>> analyzeWebsite(@PathVariable Long id) {
        try {
            Optional<Chatbot> chatbotOpt = chatbotRepository.findById(id);
            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Chatbot chatbot = chatbotOpt.get();
            
            // Start website analysis asynchronously
            CompletableFuture<List<com.noupe.chatbot.model.WebsiteContent>> analysisFuture = 
                websiteAnalysisService.analyzeWebsite(chatbot);
            
            // Return analysis status
            Map<String, Object> response = Map.of(
                "status", "analysis_started",
                "chatbotId", id,
                "websiteUrl", chatbot.getWebsiteUrl(),
                "message", "Website analysis started. Check back later for results."
            );
            
            logger.info("Started website analysis for chatbot: {}", chatbot.getName());
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
    public ResponseEntity<Map<String, Object>> indexContent(@PathVariable Long id) {
        try {
            Optional<Chatbot> chatbotOpt = chatbotRepository.findById(id);
            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Chatbot chatbot = chatbotOpt.get();
            
            // Start content indexing
            aiChatbotService.indexWebsiteContent(chatbot);
            
            Map<String, Object> response = Map.of(
                "status", "indexing_completed",
                "chatbotId", id,
                "message", "Website content has been indexed and is ready for chatbot interactions."
            );
            
            logger.info("Completed content indexing for chatbot: {}", chatbot.getName());
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
    public ResponseEntity<Map<String, Object>> getAnalytics(@PathVariable Long id) {
        try {
            Optional<Chatbot> chatbotOpt = chatbotRepository.findById(id);
            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Chatbot chatbot = chatbotOpt.get();
            
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
    public ResponseEntity<Map<String, String>> getEmbedCode(@PathVariable Long id) {
        try {
            Optional<Chatbot> chatbotOpt = chatbotRepository.findById(id);
            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Chatbot chatbot = chatbotOpt.get();
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
            <div id="noupe-chatbot-%d" data-chatbot-id="%d"></div>
            <script>
                (function() {
                    var script = document.createElement('script');
                    script.src = 'http://localhost:8080/js/chatbot-widget.js';
                    script.async = true;
                    script.onload = function() {
                        NoupeChatbot.init({
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
}
