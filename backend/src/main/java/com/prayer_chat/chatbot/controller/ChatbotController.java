package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.dto.ChatbotRequest;
import com.prayer_chat.chatbot.dto.ChatbotResponse;
import com.prayer_chat.chatbot.dto.ChatbotUpdatePayload;
import com.prayer_chat.chatbot.exception.ChatbotLimitReachedException;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
import com.prayer_chat.chatbot.service.AiChatbotService;
import com.prayer_chat.chatbot.service.AccessControlService;
import com.prayer_chat.chatbot.service.BillingModeService;
import com.prayer_chat.chatbot.service.ChatbotAccessService;
import com.prayer_chat.chatbot.service.ChatbotMutationService;
import com.prayer_chat.chatbot.service.ChatbotOnboardingService;
import com.prayer_chat.chatbot.service.ChatbotService;
import com.prayer_chat.chatbot.service.WebsiteAnalysisService;
import com.prayer_chat.chatbot.service.WebsiteSizeEstimator;
import com.prayer_chat.chatbot.config.PlanLimits;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.util.LogSanitizer;
import com.prayer_chat.chatbot.util.EmbedSecurity;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for chatbot CRUD, search, analytics, and embed code.
 * Export, content, and analysis endpoints live in dedicated controllers.
 */
@RestController
@RequestMapping("/api/chatbots")
public class ChatbotController {

    private static final Logger logger = LoggerFactory.getLogger(ChatbotController.class);

    private final ChatbotRepository chatbotRepository;
    private final ChatbotService chatbotService;
    private final AiChatbotService aiChatbotService;
    private final WebsiteAnalysisService websiteAnalysisService;
    private final WebsiteSizeEstimator websiteSizeEstimator;
    private final AccessControlService accessControlService;
    private final BillingModeService billingModeService;
    private final ChatbotAccessService chatbotAccessService;
    private final ChatbotOnboardingService chatbotOnboardingService;
    private final ChatbotMutationService chatbotMutationService;

    @Value("${app.base-url:https://chatbot-java-spring-ai.onrender.com}")
    private String baseUrl;

    @Autowired
    public ChatbotController(ChatbotRepository chatbotRepository,
                             ChatbotService chatbotService,
                             AiChatbotService aiChatbotService,
                             WebsiteAnalysisService websiteAnalysisService,
                             WebsiteSizeEstimator websiteSizeEstimator,
                             AccessControlService accessControlService,
                             BillingModeService billingModeService,
                             ChatbotAccessService chatbotAccessService,
                             ChatbotOnboardingService chatbotOnboardingService,
                             ChatbotMutationService chatbotMutationService) {
        this.chatbotRepository = chatbotRepository;
        this.chatbotService = chatbotService;
        this.aiChatbotService = aiChatbotService;
        this.websiteAnalysisService = websiteAnalysisService;
        this.websiteSizeEstimator = websiteSizeEstimator;
        this.accessControlService = accessControlService;
        this.billingModeService = billingModeService;
        this.chatbotAccessService = chatbotAccessService;
        this.chatbotOnboardingService = chatbotOnboardingService;
        this.chatbotMutationService = chatbotMutationService;
    }

    /**
     * Test/backward-compatibility constructor for older unit tests that manually instantiate
     * this controller.
     */
    public ChatbotController(ChatbotRepository chatbotRepository,
                             ChatbotService chatbotService,
                             AiChatbotService aiChatbotService,
                             WebsiteAnalysisService websiteAnalysisService,
                             com.prayer_chat.chatbot.service.ConversationExportService conversationExportService,
                             com.prayer_chat.chatbot.service.BibleVerseService bibleVerseService,
                             com.prayer_chat.chatbot.service.ChristianContentAnalysisService christianContentAnalysisService,
                             com.prayer_chat.chatbot.service.JesusTeachingsService jesusTeachingsService,
                             com.prayer_chat.chatbot.service.JesusVersesTaggingService jesusVersesTaggingService,
                             com.prayer_chat.chatbot.service.CostTrackingService costTrackingService,
                             WebsiteSizeEstimator websiteSizeEstimator,
                             com.prayer_chat.chatbot.repository.WebsiteScanAuditRepository websiteScanAuditRepository,
                             AccessControlService accessControlService,
                             com.prayer_chat.chatbot.service.RateLimitingService rateLimitingService,
                             com.prayer_chat.chatbot.service.UrlValidationService urlValidationService,
                             BillingModeService billingModeService) {
        this(chatbotRepository, chatbotService, aiChatbotService, websiteAnalysisService, websiteSizeEstimator,
            accessControlService, billingModeService,
            new ChatbotAccessService(accessControlService, chatbotRepository),
            new ChatbotOnboardingService(chatbotRepository, chatbotService, websiteAnalysisService, aiChatbotService,
                christianContentAnalysisService, websiteScanAuditRepository, accessControlService, rateLimitingService,
                costTrackingService, websiteSizeEstimator, billingModeService, urlValidationService),
            new ChatbotMutationService(chatbotRepository, urlValidationService));
    }

    @GetMapping
    public ResponseEntity<List<ChatbotResponse>> getAllChatbots(
            @AuthenticationPrincipal CustomOAuth2User currentUser) {
        User user = chatbotAccessService.requireAuthenticated(currentUser);
        chatbotAccessService.requireActiveSubscription(user);
        List<ChatbotResponse> chatbots = chatbotRepository.findByOwnerId(user.getId()).stream()
            .map(ChatbotResponse::from)
            .toList();
        return ResponseEntity.ok(chatbots);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchChatbots(
            @RequestParam String query,
            @AuthenticationPrincipal CustomOAuth2User currentUser) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Query parameter is required"));
        }
        if (query.contains("{") || query.contains("}") || query.contains("$")
            || query.contains("[") || query.contains("]")) {
            logger.warn("Rejected search query with potential NoSQL injection: {}", LogSanitizer.sanitize(query));
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid query format"));
        }

        User user = chatbotAccessService.requireAuthenticated(currentUser);
        chatbotAccessService.requireActiveSubscription(user);

        String lowerQuery = query.toLowerCase();
        List<Chatbot> results = chatbotRepository.findByOwnerId(user.getId()).stream()
            .filter(chatbot -> chatbot.getName() != null
                && chatbot.getName().toLowerCase().contains(lowerQuery)
                || (chatbot.getDescription() != null
                    && chatbot.getDescription().toLowerCase().contains(lowerQuery)))
            .toList();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChatbotResponse> getChatbot(@PathVariable Long id,
                                                      @AuthenticationPrincipal CustomOAuth2User currentUser) {
        User user = chatbotAccessService.requireAuthenticated(currentUser);
        Chatbot chatbot = chatbotAccessService.requireOwnedChatbot(user, id);
        return ResponseEntity.ok(ChatbotResponse.from(chatbot));
    }

    @PostMapping("/onboarding")
    public ResponseEntity<ChatbotResponse> createChatbotFromUrl(@RequestBody Map<String, String> request,
                                                                @AuthenticationPrincipal CustomOAuth2User currentUser) {
        User user = chatbotAccessService.requireAuthenticated(currentUser);
        String websiteUrl = request.get("websiteUrl");
        if (websiteUrl == null || websiteUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Website URL is required");
        }
        Chatbot savedChatbot = chatbotOnboardingService.createFromWebsiteUrl(user, websiteUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(ChatbotResponse.from(savedChatbot));
    }

    @PostMapping
    public ResponseEntity<?> createChatbot(@Valid @RequestBody ChatbotRequest request,
                                           @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            User user = currentUser.getUser();
            if (!accessControlService.hasActiveSubscription(user) && !accessControlService.isPreviewMode(user)) {
                logger.warn("User {} attempted to create chatbot without active subscription or preview mode",
                    LogSanitizer.sanitize(user.getEmail()));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "Active subscription or preview mode required to create chatbots."
                ));
            }

            if (request.getWebsiteUrl() != null && !request.getWebsiteUrl().trim().isEmpty()) {
                String websiteUrlCreate = request.getWebsiteUrl();
                if (!websiteUrlCreate.startsWith("http://") && !websiteUrlCreate.startsWith("https://")) {
                    websiteUrlCreate = "https://" + websiteUrlCreate;
                }

                int estimatedPagesCreate = websiteSizeEstimator.estimateSize(websiteUrlCreate);
                int maxPagesCreate = PlanLimits.maxPagesPerScan(accessControlService.getSubscriptionPlan(user));
                if (estimatedPagesCreate > maxPagesCreate) {
                    Subscription.SubscriptionPlan suggested = PlanLimits.minimumPlanForPages(estimatedPagesCreate);
                    int suggestedMaxPages = PlanLimits.maxPagesPerScan(suggested);
                    logger.warn("User {} attempted to create chatbot with website of {} pages (limit: {})",
                        LogSanitizer.sanitize(user.getEmail()), estimatedPagesCreate, maxPagesCreate);
                    HttpStatus createSizeStatus = billingModeService.isBillingEnabled()
                        ? HttpStatus.PAYMENT_REQUIRED
                        : HttpStatus.FORBIDDEN;
                    return ResponseEntity.status(createSizeStatus).body(
                        billingModeService.websiteTooLargePayload(
                            estimatedPagesCreate, maxPagesCreate, suggested, suggestedMaxPages));
                }
            }

            Chatbot chatbot = new Chatbot();
            chatbot.setName(request.getName());
            chatbot.setWebsiteUrl(request.getWebsiteUrl());
            chatbot.setDescription(request.getDescription());
            chatbot.setPrimaryLanguage(request.getPrimaryLanguage());

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
            chatbot.setBrandingConfig(EmbedSecurity.sanitizeBrandingConfig(request.getBrandingConfig()));
            chatbot.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
            chatbot.setEmbedCode(com.prayer_chat.chatbot.model.Chatbot.generateNewEmbedCode());

            int maxAllowed = accessControlService.getMaxChatbotsAllowed(user);
            Chatbot savedChatbot = chatbotService.createChatbotEnforcingLimit(chatbot, user, maxAllowed);
            logger.info("Created new chatbot: {} for user: {}",
                LogSanitizer.sanitize(savedChatbot.getName()), LogSanitizer.sanitize(user.getEmail()));
            return ResponseEntity.status(HttpStatus.CREATED).body(ChatbotResponse.from(savedChatbot));
        } catch (ChatbotLimitReachedException e) {
            logger.warn("User attempted to create chatbot but limit reached (current: {}, max: {})",
                e.getCurrentCount(), e.getMaxAllowed());
            String limitMsg = billingModeService.isBillingEnabled()
                ? "Chatbot limit reached. Preview mode allows " + e.getMaxAllowed() + " chatbots. Upgrade to create more."
                : "You already have the maximum number of chatbots allowed for your account (" + e.getMaxAllowed() + ").";
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", limitMsg,
                "currentCount", e.getCurrentCount(),
                "maxAllowed", e.getMaxAllowed(),
                "upgradeRequired", billingModeService.shouldSuggestPaidUpgrade()
            ));
        } catch (Exception e) {
            logger.error("Error creating chatbot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @RequestMapping(value = "/{id}", method = { RequestMethod.PUT, RequestMethod.PATCH })
    public ResponseEntity<ChatbotResponse> updateChatbot(@PathVariable Long id,
                                                         @Valid @RequestBody ChatbotUpdatePayload patch,
                                                         @AuthenticationPrincipal CustomOAuth2User currentUser) {
        User user = chatbotAccessService.requireAuthenticated(currentUser);
        Chatbot chatbot = chatbotAccessService.requireOwnedChatbot(user, id);
        Chatbot updated = chatbotMutationService.applyPatch(chatbot, patch);
        return ResponseEntity.ok(ChatbotResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteChatbot(@PathVariable Long id,
                                              @AuthenticationPrincipal CustomOAuth2User currentUser) {
        User user = chatbotAccessService.requireAuthenticated(currentUser);
        if (id == null || id < 1) {
            return ResponseEntity.badRequest().build();
        }
        chatbotAccessService.requireActiveSubscription(user);
        Optional<Chatbot> chatbotOpt = chatbotRepository.findByIdAndOwner_Id(id, user.getId());
        if (chatbotOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Chatbot chatbot = chatbotOpt.get();
        Long chatbotId = chatbot.getId();
        try {
            aiChatbotService.deleteVectorStoreDocumentsForChatbot(chatbotId);
        } catch (Exception e) {
            logger.warn("Vector store cleanup before delete chatbot {}: {}", chatbotId, e.getMessage());
        }
        chatbotRepository.delete(chatbot);
        logger.info("User {} deleted chatbot {}", user.getId(), chatbotId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics(@PathVariable Long id,
                                                            @AuthenticationPrincipal CustomOAuth2User currentUser) {
        User user = chatbotAccessService.requireAuthenticated(currentUser);
        Chatbot chatbot = chatbotAccessService.requireOwnedChatbot(user, id);

        Map<String, Object> conversationAnalytics = aiChatbotService.getConversationAnalytics(id);
        Map<String, Object> analysisStats = websiteAnalysisService.getAnalysisStats(chatbot);

        Map<String, Object> analytics = Map.of(
            "chatbotId", id,
            "chatbotName", chatbot.getName(),
            "conversations", conversationAnalytics,
            "websiteAnalysis", analysisStats,
            "status", chatbot.getIsActive() ? "active" : "inactive"
        );
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/{id}/embed")
    public ResponseEntity<?> getEmbedCode(@PathVariable Long id,
                                          @AuthenticationPrincipal CustomOAuth2User currentUser) {
        User user = chatbotAccessService.requireAuthenticated(currentUser);
        Chatbot chatbot = chatbotAccessService.requireOwnedChatbot(user, id);

        if (!accessControlService.canAccessIntegrationScript(user)) {
            logger.warn("User {} attempted to access integration script without paid subscription",
                LogSanitizer.sanitize(user.getEmail()));
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of(
                "error", "Upgrade to paid tier for integration script access. Preview mode does not include integration script access.",
                "upgradeRequired", billingModeService.shouldSuggestPaidUpgrade(),
                "message", "We'd love to help you share your message more widely! Upgrade to access the integration script."
            ));
        }

        String embedCode = generateEmbedCode(chatbot);
        Map<String, String> response = Map.of(
            "embedCode", embedCode,
            "chatbotId", chatbot.getId().toString(),
            "chatbotName", chatbot.getName()
        );
        return ResponseEntity.ok(response);
    }

    private static final String DEFAULT_BASE_URL = "https://chatbot-java-spring-ai.onrender.com";
    private static final String DEPRECATED_BASE_URL = "https://chatbot-backend-4mp4.onrender.com";
    private static final String DEPRECATED_BASE_URL_2 = "https://prayer-chat-backend-web-service.onrender.com";

    private String generateEmbedCode(Chatbot chatbot) {
        String configured = baseUrl == null ? "" : baseUrl.trim();
        String configuredNoSlash = configured.replaceAll("/$", "");
        if (DEPRECATED_BASE_URL.equals(configured) || DEPRECATED_BASE_URL.equals(configuredNoSlash)
            || DEPRECATED_BASE_URL_2.equals(configured) || DEPRECATED_BASE_URL_2.equals(configuredNoSlash)) {
            configured = DEFAULT_BASE_URL;
        }
        String cleanBaseUrl = EmbedSecurity.validateAndNormalizeBaseUrl(configured, DEFAULT_BASE_URL);
        String safeForJs = EmbedSecurity.escapeForJsString(cleanBaseUrl);
        String embedCode = chatbot.getEmbedCode();
        if (embedCode == null || embedCode.isBlank()) {
            throw new IllegalStateException("Chatbot embedCode is missing");
        }
        embedCode = embedCode.trim();
        if (!embedCode.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalStateException("Chatbot embedCode contains unsafe characters");
        }
        String safeEmbedForJs = EmbedSecurity.escapeForJsString(embedCode);
        return String.format("""
            <div id="prayer-chat-chatbot-%s" data-embed-code="%s"></div>
            <script>
            (function() {
                var embedCode = '%s';
                var baseUrl = '%s';
                var script = document.createElement('script');
                script.src = baseUrl + '/js/chatbot-widget.js';
                script.async = true;
                script.onerror = function() {
                    var el = document.getElementById('prayer-chat-chatbot-' + embedCode) || document.querySelector('[data-embed-code=\"' + embedCode + '\"]');
                    if (el) el.innerHTML = '<p style="padding:12px;background:#fff3cd;border:1px solid #ffc107;border-radius:8px;font-family:sans-serif;font-size:14px;">Chat could not load. Check browser console (F12) or Content-Security-Policy.</p>';
                };
                script.onload = function() {
                    if (typeof PrayerChat !== 'undefined' && PrayerChat.init) {
                        PrayerChat.init({ embedCode: embedCode, apiUrl: baseUrl + '/api' });
                    } else {
                        var el = document.getElementById('prayer-chat-chatbot-' + embedCode) || document.querySelector('[data-embed-code=\"' + embedCode + '\"]');
                        if (el) el.innerHTML = '<p style="padding:12px;background:#f8d7da;border:1px solid #f5c6cb;border-radius:8px;font-family:sans-serif;font-size:14px;">Chat failed to start. Open console (F12) for details.</p>';
                    }
                };
                document.head.appendChild(script);
            })();
            </script>
            """, embedCode, embedCode, safeEmbedForJs, safeForJs);
    }
}
