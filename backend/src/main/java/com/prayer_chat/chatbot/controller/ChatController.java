package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.dto.ChatRequest;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.service.AiChatbotService;
import com.prayer_chat.chatbot.service.RateLimitingService;
import com.prayer_chat.chatbot.util.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST Controller for chat interactions
 */
@RestController
@RequestMapping("/api/chat")
@Validated
public class ChatController {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
    private final AiChatbotService aiChatbotService;
    private final ChatbotRepository chatbotRepository;
    private final RateLimitingService rateLimitingService;
    
    @Autowired
    public ChatController(AiChatbotService aiChatbotService, 
                         ChatbotRepository chatbotRepository,
                         RateLimitingService rateLimitingService) {
        this.aiChatbotService = aiChatbotService;
        this.chatbotRepository = chatbotRepository;
        this.rateLimitingService = rateLimitingService;
    }
    
    /**
     * Send a message to a chatbot
     */
    @PostMapping("/{chatbotId}")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @PathVariable Long chatbotId,
            @Valid @RequestBody ChatRequest request,
            HttpServletRequest httpRequest) {

        try {
            // Get chatbot and verify it exists
            Optional<Chatbot> chatbotOpt = chatbotRepository.findById(chatbotId);
            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "Chatbot not found"
                ));
            }
            
            Chatbot chatbot = chatbotOpt.get();
            
            // SECURITY: Check if chatbot is active
            if (!chatbot.getIsActive()) {
                logger.warn("Attempted to send message to inactive chatbot: {}", chatbotId);
                return ResponseEntity.status(403).body(Map.of(
                    "error", "Chatbot is not active"
                ));
            }
            
            // SECURITY: Check rate limit if chatbot has an owner
            // Note: Rate limiting is per chatbot owner, not per end-user sending messages
            // This prevents abuse by chatbot owners, not by end-users
            if (chatbot.getOwner() != null) {
                User owner = chatbot.getOwner();
                RateLimitingService.RateLimitResult rateLimitResult = rateLimitingService.checkMessageLimit(owner);
                
                if (!rateLimitResult.isAllowed()) {
                    logger.warn("User {} attempted to send message but rate limit reached (current: {}, limit: {})", 
                        owner.getId(), rateLimitResult.getCurrent(), rateLimitResult.getLimit());
                    return ResponseEntity.status(429).body(Map.of(
                        "error", rateLimitResult.getErrorMessage(),
                        "current", rateLimitResult.getCurrent(),
                        "limit", rateLimitResult.getLimit(),
                        "upgradeRequired", rateLimitResult.isPreviewMode()
                    ));
                }
            }
            
            // Extract and validate request data
            String message = request.getMessage();
            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.trim().isEmpty()) {
                sessionId = generateSessionId();
            }

            String userLanguage = request.getLanguage();
            if (userLanguage == null || userLanguage.trim().isEmpty()) {
                userLanguage = "en";
            }

            // Get user info
            String userIp = getClientIpAddress(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");

            // Process message
            ChatResponse response = aiChatbotService.processMessage(
                chatbotId, message, sessionId, userLanguage, userIp, userAgent
            );

            // Extract response content
            String responseContent = response.getResult().getOutput().getText();

            // Return response
            Map<String, Object> responseData = Map.of(
                "message", responseContent,
                "sessionId", sessionId,
                "timestamp", System.currentTimeMillis(),
                "chatbotId", chatbotId
            );

            return ResponseEntity.ok(responseData);

        } catch (RuntimeException e) {
            String errorMessage = e.getMessage();
            logger.error("Error processing chat message for chatbot {}: {}", chatbotId, LogSanitizer.sanitizeException(e));
            logger.error("Exception type: {}, Cause: {}", e.getClass().getName(), 
                e.getCause() != null ? e.getCause().getClass().getName() : "none");
            
            // Return specific error messages for common issues
            if (errorMessage != null && errorMessage.contains("not found")) {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "Chatbot not found"
                ));
            }
            if (errorMessage != null && errorMessage.contains("not active")) {
                return ResponseEntity.status(403).body(Map.of(
                    "error", "Chatbot is not active"
                ));
            }
            
            // Check for AI configuration errors
            if (errorMessage != null && (errorMessage.contains("ANTHROPIC_API_KEY") || 
                                         errorMessage.contains("ChatModel") || 
                                         errorMessage.contains("not properly configured"))) {
                logger.error("AI service configuration error detected. Check ANTHROPIC_API_KEY and COHERE_API_KEY environment variables.");
                return ResponseEntity.status(503).body(Map.of(
                    "error", "AI service is not configured. Please contact support."
                ));
            }
            
            return ResponseEntity.status(500).body(Map.of(
                "error", errorMessage != null ? errorMessage : "Failed to process message"
            ));
        } catch (Exception e) {
            logger.error("Unexpected error processing chat message for chatbot {}: {}", chatbotId, LogSanitizer.sanitizeException(e));
            logger.error("Exception type: {}, Cause: {}", e.getClass().getName(), 
                e.getCause() != null ? e.getCause().getClass().getName() : "none");
            
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("ANTHROPIC_API_KEY") || 
                                     errorMsg.contains("ChatModel") || 
                                     errorMsg.contains("not properly configured"))) {
                return ResponseEntity.status(503).body(Map.of(
                    "error", "AI service is not configured. Please contact support."
                ));
            }
            
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to process message: " + (errorMsg != null ? errorMsg : "Unknown error")
            ));
        }
    }
    
    /**
     * Get chatbot by embed code
     */
    @GetMapping("/embed/{embedCode}")
    public ResponseEntity<Map<String, Object>> getChatbotByEmbedCode(@PathVariable String embedCode) {
        try {
            Optional<Chatbot> chatbotOpt = chatbotRepository.findByEmbedCode(embedCode);
            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Chatbot chatbot = chatbotOpt.get();
            
            if (!chatbot.getIsActive()) {
                return ResponseEntity.status(403).body(Map.of(
                    "error", "Chatbot is not active"
                ));
            }
            
            Map<String, Object> response = Map.of(
                "chatbotId", chatbot.getId(),
                "name", chatbot.getName(),
                "description", chatbot.getDescription() != null ? chatbot.getDescription() : "",
                "primaryLanguage", chatbot.getPrimaryLanguage(),
                "supportedLanguages", chatbot.getSupportedLanguages(),
                "brandingConfig", chatbot.getBrandingConfig() != null ? chatbot.getBrandingConfig() : "{}"
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving chatbot by embed code {}: {}", LogSanitizer.sanitize(embedCode), LogSanitizer.sanitizeException(e));
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to retrieve chatbot"
            ));
        }
    }
    
    /**
     * Get conversation history
     */
    @GetMapping("/{chatbotId}/conversation/{sessionId}")
    public ResponseEntity<Map<String, Object>> getConversationHistory(
            @PathVariable Long chatbotId,
            @PathVariable String sessionId) {
        
        try {
            // This would typically return conversation history
            // For now, return a simple response
            Map<String, Object> response = Map.of(
                "chatbotId", chatbotId,
                "sessionId", sessionId,
                "messages", "[]", // Would contain actual message history
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving conversation history for chatbot {} session {}: {}", chatbotId, LogSanitizer.sanitize(sessionId), LogSanitizer.sanitizeException(e));
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to retrieve conversation history"
            ));
        }
    }
    
    /**
     * Generate a unique session ID
     */
    private String generateSessionId() {
        return "session_" + UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * Get client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
