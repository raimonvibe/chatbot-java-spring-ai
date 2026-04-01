package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.dto.ChatRequest;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.service.AiChatbotService;
import com.prayer_chat.chatbot.service.BillingModeService;
import com.prayer_chat.chatbot.service.RateLimitingService;
import com.prayer_chat.chatbot.util.LogSanitizer;
import com.prayer_chat.chatbot.util.EmbedSecurity;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST controller for chat interactions.
 *
 * <p><b>Message limits (server-enforced):</b>
 * <ul>
 *   <li>{@code POST /api/chat/{chatbotId}} — used by the in-app preview and authenticated clients. Applies
 *       per-IP per-chatbot throttling (same bucket as below) and, when the chatbot has an owner,
 *       {@link RateLimitingService#checkMessageLimit} using {@link com.prayer_chat.chatbot.service.BillingModeService#effectiveMessagesPerDay}.
 *       When billing is enabled, plan quotas come from {@link com.prayer_chat.chatbot.config.PlanLimits#messagesPerDay}
 *       (FREE = 10/day, BASIC = 100/day, …). When billing is disabled, a higher free-product ceiling applies.</li>
 *   <li>{@code POST /api/chat/embed/{embedCode}} — same owner daily quota as above, plus an additional
 *       {@value #EMBED_CHAT_PER_IP_PER_CHATBOT_LIMIT} messages per hour per client IP per chatbot so one visitor
 *       cannot exhaust the site owner’s quota.</li>
 * </ul>
 *
 * <p>Website scan / page caps are enforced separately in {@code ChatbotController} and crawl configuration
 * ({@code app.website-analysis.max-pages}), not in this class.
 */
@RestController
@RequestMapping("/api/chat")
@Validated
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    /** Per-IP per-chatbot limit for embed chat: 30 messages per hour to prevent one visitor from exhausting quota. */
    private static final int EMBED_CHAT_PER_IP_PER_CHATBOT_LIMIT = 30;
    private static final Duration EMBED_CHAT_REFILL_PERIOD = Duration.ofHours(1);

    private final Map<String, Bucket> embedChatBuckets = new ConcurrentHashMap<>();

    private final AiChatbotService aiChatbotService;
    private final ChatbotRepository chatbotRepository;
    private final RateLimitingService rateLimitingService;
    private final BillingModeService billingModeService;

    @Autowired
    public ChatController(AiChatbotService aiChatbotService,
                         ChatbotRepository chatbotRepository,
                         RateLimitingService rateLimitingService,
                         BillingModeService billingModeService) {
        this.aiChatbotService = aiChatbotService;
        this.chatbotRepository = chatbotRepository;
        this.rateLimitingService = rateLimitingService;
        this.billingModeService = billingModeService;
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
            
            // SECURITY: Per-IP per-chatbot rate limit (embed abuse prevention): one visitor cannot exhaust owner quota
            String clientIp = getClientIpAddress(httpRequest);
            String embedBucketKey = "embed:" + clientIp + ":" + chatbotId;
            Bucket bucket = embedChatBuckets.computeIfAbsent(embedBucketKey, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(EMBED_CHAT_PER_IP_PER_CHATBOT_LIMIT, Refill.intervally(EMBED_CHAT_PER_IP_PER_CHATBOT_LIMIT, EMBED_CHAT_REFILL_PERIOD)))
                .build());
            if (!bucket.tryConsume(1)) {
                logger.warn("Embed chat rate limit exceeded for IP {} chatbot {}", clientIp, chatbotId);
                return ResponseEntity.status(429).body(Map.of(
                    "error", "Too many messages. Please try again later."
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
                        "upgradeRequired", rateLimitResult.isUpgradeSuggested()
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
     * Send a message to a chatbot via opaque embed code.
     * This prevents simple numeric ID swapping in embed snippets.
     */
    @PostMapping("/embed/{embedCode}")
    public ResponseEntity<Map<String, Object>> sendMessageByEmbedCode(
            @PathVariable String embedCode,
            @Valid @RequestBody ChatRequest request,
            HttpServletRequest httpRequest) {

        try {
            if (embedCode == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Embed code is required"));
            }
            String trimmed = embedCode.trim();
            if (trimmed.isEmpty() || trimmed.length() > MAX_EMBED_CODE_OR_ID_LENGTH) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid embed code"));
            }
            // Embed code is generated by us; restrict chars to reduce injection surface.
            if (!trimmed.matches("^[a-zA-Z0-9_-]+$")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid embed code"));
            }

            Optional<Chatbot> chatbotOpt = chatbotRepository.findByEmbedCode(trimmed);
            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Chatbot not found"));
            }

            Chatbot chatbot = chatbotOpt.get();

            // SECURITY: Check if chatbot is active
            if (!chatbot.getIsActive()) {
                logger.warn("Attempted to send message to inactive chatbot: {}", chatbot.getId());
                return ResponseEntity.status(403).body(Map.of("error", "Chatbot is not active"));
            }

            // SECURITY: Per-IP per-chatbot rate limit
            String clientIp = getClientIpAddress(httpRequest);
            Long id = chatbot.getId();
            String embedBucketKey = "embed:" + clientIp + ":" + id;
            Bucket bucket = embedChatBuckets.computeIfAbsent(embedBucketKey, k -> Bucket.builder()
                    .addLimit(Bandwidth.classic(EMBED_CHAT_PER_IP_PER_CHATBOT_LIMIT, Refill.intervally(EMBED_CHAT_PER_IP_PER_CHATBOT_LIMIT, EMBED_CHAT_REFILL_PERIOD)))
                    .build());
            if (!bucket.tryConsume(1)) {
                logger.warn("Embed chat rate limit exceeded for IP {} chatbot {}", clientIp, id);
                return ResponseEntity.status(429).body(Map.of(
                        "error", "Too many messages. Please try again later."
                ));
            }

            // SECURITY: Check rate limit if chatbot has an owner
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
                            "upgradeRequired", rateLimitResult.isUpgradeSuggested()
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
                    id, message, sessionId, userLanguage, userIp, userAgent
            );

            String responseContent = response.getResult().getOutput().getText();

            Map<String, Object> responseData = Map.of(
                    "message", responseContent,
                    "sessionId", sessionId,
                    "timestamp", System.currentTimeMillis(),
                    "chatbotId", id
            );

            return ResponseEntity.ok(responseData);
        } catch (RuntimeException e) {
            String errorMessage = e.getMessage();
            logger.error("Error processing embed chat message for embedCode {}: {}", LogSanitizer.sanitize(embedCode), LogSanitizer.sanitizeException(e));

            if (errorMessage != null && errorMessage.contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", "Chatbot not found"));
            }
            if (errorMessage != null && errorMessage.contains("not active")) {
                return ResponseEntity.status(403).body(Map.of("error", "Chatbot is not active"));
            }
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
            logger.error("Unexpected error processing embed chat message for embedCode {}: {}", LogSanitizer.sanitize(embedCode), LogSanitizer.sanitizeException(e));
            String errorMsg = e.getMessage();
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to process message: " + (errorMsg != null ? errorMsg : "Unknown error")
            ));
        }
    }
    
    /** Max length for embed code or ID path variable to prevent DoS / abuse. */
    private static final int MAX_EMBED_CODE_OR_ID_LENGTH = 255;

    /**
     * Get chatbot config for widget: by opaque embed code only.
     * Security: removes numeric-ID fallback to prevent simple ID swapping.
     */
    @GetMapping("/embed/{embedCode}")
    public ResponseEntity<Map<String, Object>> getChatbotByEmbedCode(@PathVariable String embedCode) {
        try {
            if (embedCode == null || embedCode.length() > MAX_EMBED_CODE_OR_ID_LENGTH) {
                return ResponseEntity.badRequest().build();
            }
            String trimmed = embedCode.trim();
            if (trimmed.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Embed code is generated by us; restrict chars to reduce injection surface.
            if (!trimmed.matches("^[a-zA-Z0-9_-]+$")) {
                return ResponseEntity.badRequest().build();
            }

            Optional<Chatbot> chatbotOpt = chatbotRepository.findByEmbedCode(trimmed);
            if (chatbotOpt.isEmpty()) return ResponseEntity.notFound().build();

            Chatbot chatbot = chatbotOpt.get();
            
            if (!chatbot.getIsActive()) {
                return ResponseEntity.status(403).body(Map.of(
                    "error", "Chatbot is not active"
                ));
            }

            // Only expose safe, non-sensitive fields. Sanitize brandingConfig and strip angle brackets from name/description (XSS defense).
            String safeBranding = EmbedSecurity.sanitizeBrandingConfig(chatbot.getBrandingConfig());
            String name = EmbedSecurity.stripAngleBrackets(chatbot.getName() != null ? chatbot.getName() : "");
            String description = EmbedSecurity.stripAngleBrackets(chatbot.getDescription() != null ? chatbot.getDescription() : "");
            if (description.length() > 500) description = description.substring(0, 500);
            String primaryLanguage = chatbot.getPrimaryLanguage() != null ? chatbot.getPrimaryLanguage() : "en";
            List<String> supportedLanguages = chatbot.getSupportedLanguages() != null ? chatbot.getSupportedLanguages() : List.of();
            String safeAvatarId = EmbedSecurity.validateAvatarId(chatbot.getAvatarId());

            Map<String, Object> response = new java.util.HashMap<>(Map.of(
                "name", name,
                "description", description,
                "primaryLanguage", primaryLanguage,
                "supportedLanguages", supportedLanguages,
                "brandingConfig", safeBranding
            ));
            if (safeAvatarId != null) {
                response.put("avatar", safeAvatarId);
            }

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
