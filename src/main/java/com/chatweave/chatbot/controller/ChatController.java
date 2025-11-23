package com.chatweave.chatbot.controller;

import com.chatweave.chatbot.dto.ChatRequest;
import com.chatweave.chatbot.model.Chatbot;
import com.chatweave.chatbot.repository.ChatbotRepository;
import com.chatweave.chatbot.service.AiChatbotService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
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
    
    @Autowired
    public ChatController(AiChatbotService aiChatbotService, ChatbotRepository chatbotRepository) {
        this.aiChatbotService = aiChatbotService;
        this.chatbotRepository = chatbotRepository;
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
            String responseContent = response.getResult().getOutput().getContent();

            // Return response
            Map<String, Object> responseData = Map.of(
                "message", responseContent,
                "sessionId", sessionId,
                "timestamp", System.currentTimeMillis(),
                "chatbotId", chatbotId
            );

            return ResponseEntity.ok(responseData);

        } catch (Exception e) {
            logger.error("Error processing chat message for chatbot {}", chatbotId, e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to process message"
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
            logger.error("Error retrieving chatbot by embed code {}", embedCode, e);
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
            logger.error("Error retrieving conversation history for chatbot {} session {}", chatbotId, sessionId, e);
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
