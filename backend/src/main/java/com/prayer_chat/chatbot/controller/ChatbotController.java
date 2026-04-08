package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.dto.ChatbotRequest;
import com.prayer_chat.chatbot.dto.ChatbotUpdatePayload;
import com.prayer_chat.chatbot.exception.ChatbotLimitReachedException;
import com.prayer_chat.chatbot.model.BibleVerse;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
import com.prayer_chat.chatbot.service.AiChatbotService;
import com.prayer_chat.chatbot.service.ChatbotService;
import com.prayer_chat.chatbot.service.WebsiteAnalysisService;
import com.prayer_chat.chatbot.service.ConversationExportService;
import com.prayer_chat.chatbot.service.BibleVerseService;
import com.prayer_chat.chatbot.service.ChristianContentAnalysisService;
import com.prayer_chat.chatbot.dto.ChristianContentAnalysis;
import com.prayer_chat.chatbot.service.JesusTeachingsService;
import com.prayer_chat.chatbot.service.JesusVersesTaggingService;
import com.prayer_chat.chatbot.dto.JesusTeaching;
import com.prayer_chat.chatbot.service.CostTrackingService;
import com.prayer_chat.chatbot.service.WebsiteSizeEstimator;
import com.prayer_chat.chatbot.config.PlanLimits;
import com.prayer_chat.chatbot.service.AccessControlService;
import com.prayer_chat.chatbot.service.RateLimitingService;
import com.prayer_chat.chatbot.service.BillingModeService;
import com.prayer_chat.chatbot.service.UrlValidationService;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.repository.ConversationRepository;
import com.prayer_chat.chatbot.repository.WebsiteScanAuditRepository;
import com.prayer_chat.chatbot.model.WebsiteScanAudit;
import com.prayer_chat.chatbot.model.Conversation;
import com.prayer_chat.chatbot.util.LogSanitizer;
import com.prayer_chat.chatbot.util.EmbedSecurity;
import com.prayer_chat.chatbot.util.WebsiteDisplayName;
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

import com.prayer_chat.chatbot.model.WebsiteContent;
import com.prayer_chat.chatbot.service.ChristianContentAnalysisService.BibleVerseMatch;

/**
 * REST Controller for chatbot management
 */
@RestController
@RequestMapping("/api/chatbots")
public class ChatbotController {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatbotController.class);
    
    private final ChatbotRepository chatbotRepository;
    private final ConversationRepository conversationRepository;
    private final ChatbotService chatbotService;
    private final AiChatbotService aiChatbotService;
    private final WebsiteAnalysisService websiteAnalysisService;
    private final ConversationExportService conversationExportService;
    private final BibleVerseService bibleVerseService;
    private final ChristianContentAnalysisService christianContentAnalysisService;
    private final JesusTeachingsService jesusTeachingsService;
    private final JesusVersesTaggingService jesusVersesTaggingService;
    private final CostTrackingService costTrackingService;
    private final WebsiteSizeEstimator websiteSizeEstimator;
    private final WebsiteScanAuditRepository websiteScanAuditRepository;
    private final AccessControlService accessControlService;
    private final RateLimitingService rateLimitingService;
    private final UrlValidationService urlValidationService;
    private final BillingModeService billingModeService;

    @Value("${app.base-url:https://chatbot-java-spring-ai.onrender.com}")
    private String baseUrl;

    @Autowired
    public ChatbotController(ChatbotRepository chatbotRepository,
                           ConversationRepository conversationRepository,
                           ChatbotService chatbotService,
                           AiChatbotService aiChatbotService,
                           WebsiteAnalysisService websiteAnalysisService,
                           ConversationExportService conversationExportService,
                           BibleVerseService bibleVerseService,
                           ChristianContentAnalysisService christianContentAnalysisService,
                           JesusTeachingsService jesusTeachingsService,
                           JesusVersesTaggingService jesusVersesTaggingService,
                           CostTrackingService costTrackingService,
                           WebsiteSizeEstimator websiteSizeEstimator,
                           WebsiteScanAuditRepository websiteScanAuditRepository,
                           AccessControlService accessControlService,
                           RateLimitingService rateLimitingService,
                           UrlValidationService urlValidationService,
                           BillingModeService billingModeService) {
        this.chatbotRepository = chatbotRepository;
        this.conversationRepository = conversationRepository;
        this.chatbotService = chatbotService;
        this.aiChatbotService = aiChatbotService;
        this.websiteAnalysisService = websiteAnalysisService;
        this.conversationExportService = conversationExportService;
        this.bibleVerseService = bibleVerseService;
        this.christianContentAnalysisService = christianContentAnalysisService;
        this.jesusTeachingsService = jesusTeachingsService;
        this.jesusVersesTaggingService = jesusVersesTaggingService;
        this.costTrackingService = costTrackingService;
        this.websiteSizeEstimator = websiteSizeEstimator;
        this.websiteScanAuditRepository = websiteScanAuditRepository;
        this.accessControlService = accessControlService;
        this.rateLimitingService = rateLimitingService;
        this.urlValidationService = urlValidationService;
        this.billingModeService = billingModeService;
    }

    /**
     * Test/backward-compatibility constructor for older unit tests that manually instantiate
     * this controller and do not need conversation-level export authorization checks.
     */
    public ChatbotController(ChatbotRepository chatbotRepository,
                           ChatbotService chatbotService,
                           AiChatbotService aiChatbotService,
                           WebsiteAnalysisService websiteAnalysisService,
                           ConversationExportService conversationExportService,
                           BibleVerseService bibleVerseService,
                           ChristianContentAnalysisService christianContentAnalysisService,
                           JesusTeachingsService jesusTeachingsService,
                           JesusVersesTaggingService jesusVersesTaggingService,
                           CostTrackingService costTrackingService,
                           WebsiteSizeEstimator websiteSizeEstimator,
                           WebsiteScanAuditRepository websiteScanAuditRepository,
                           AccessControlService accessControlService,
                           RateLimitingService rateLimitingService,
                           UrlValidationService urlValidationService,
                           BillingModeService billingModeService) {
        this(chatbotRepository, null, chatbotService, aiChatbotService, websiteAnalysisService, conversationExportService,
            bibleVerseService, christianContentAnalysisService, jesusTeachingsService, jesusVersesTaggingService,
            costTrackingService, websiteSizeEstimator, websiteScanAuditRepository, accessControlService,
            rateLimitingService, urlValidationService, billingModeService);
    }

    // ============================================================================
    // HELPER METHODS FOR AUTHORIZATION AND SUBSCRIPTION
    // ============================================================================

    /**
     * Check if user has an active subscription or is in preview mode
     */
    private boolean hasActiveSubscription(User user) {
        // Use AccessControlService to check subscription (includes preview mode)
        // Service is always injected via constructor, so no null check needed
        return accessControlService.hasActiveSubscription(user) || accessControlService.isPreviewMode(user);
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
     * @Transactional ensures Hibernate session stays open during JSON serialization
     */
    @GetMapping
    @Transactional(readOnly = true)
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

            // Use findByOwnerId to avoid loading all chatbots and filtering in memory
            // This is more efficient and avoids potential lazy loading issues
            List<Chatbot> chatbots = chatbotRepository.findByOwnerId(user.getId());

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
     * @Transactional ensures Hibernate session stays open during JSON serialization
     */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
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
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Chatbot not found"));
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
     * Get website analysis status for a chatbot. Used by frontend to keep loading screen until content is ready.
     * <p>
     * Security: same gate as {@link #getChatbot(Long, CustomOAuth2User)} — authenticated owner with subscription/preview.
     * Prevents IDOR and leakage of indexing metadata ({@code pagesIndexed}) to unauthenticated or unrelated users.
     * Filter chain allows GET {@code /api/chatbots/**} without auth, but this handler returns 401 without a principal.
     */
    @GetMapping("/{id}/analysis-status")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getAnalysisStatus(@PathVariable Long id,
                                                                 @AuthenticationPrincipal CustomOAuth2User currentUser) {
        if (id == null || id < 0) {
            return ResponseEntity.badRequest().build();
        }
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
        Map<String, Object> status = websiteAnalysisService.getAnalysisStatus(chatbot);
        return ResponseEntity.ok(status);
    }
    
    /**
     * Simplified onboarding endpoint - creates chatbot from website URL only
     * Auto-generates name, pre-configures Christian values, and starts analysis
     */
    @PostMapping("/onboarding")
    public ResponseEntity<?> createChatbotFromUrl(@RequestBody Map<String, String> request,
                                                  @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            User user = currentUser.getUser();
            String websiteUrl = request.get("websiteUrl");

            if (websiteUrl == null || websiteUrl.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Website URL is required"));
            }

            // SECURITY: normalize and reject unsafe URLs before name generation, persistence, or crawling (SSRF defense).
            Optional<String> canonicalWebsite = urlValidationService.completeAndValidate(websiteUrl);
            if (canonicalWebsite.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid or unsafe website URL"));
            }
            websiteUrl = canonicalWebsite.get();

            // Check if user already has chatbots (onboarding is only for first chatbot)
            Long currentChatbotCount = chatbotRepository.countByOwner(user.getId());
            if (currentChatbotCount > 0) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "Onboarding endpoint is only for creating your first chatbot. Use /api/chatbots to create additional chatbots."
                ));
            }

            // Check if user has active subscription (or is in preview mode)
            if (!accessControlService.hasActiveSubscription(user) && !accessControlService.isPreviewMode(user)) {
                logger.warn("User {} attempted onboarding without active subscription or preview mode", LogSanitizer.sanitize(user.getEmail()));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "Active subscription or preview mode required to create chatbots."
                ));
            }

            // Check website size limit BEFORE creating chatbot (prevents costs for large sites)
            int estimatedPagesOnboarding = websiteSizeEstimator.estimateSize(websiteUrl);
            int maxPagesOnboarding = PlanLimits.maxPagesPerScan(accessControlService.getSubscriptionPlan(user));
            if (estimatedPagesOnboarding > maxPagesOnboarding) {
                Subscription.SubscriptionPlan suggested = PlanLimits.minimumPlanForPages(estimatedPagesOnboarding);
                int suggestedMaxPages = PlanLimits.maxPagesPerScan(suggested);
                logger.warn("User {} attempted to create chatbot with website of {} pages (limit: {})",
                    LogSanitizer.sanitize(user.getEmail()), estimatedPagesOnboarding, maxPagesOnboarding);
                HttpStatus onboardingSizeStatus = billingModeService.isBillingEnabled()
                    ? HttpStatus.PAYMENT_REQUIRED
                    : HttpStatus.FORBIDDEN;
                return ResponseEntity.status(onboardingSizeStatus).body(
                    billingModeService.websiteTooLargePayload(
                        estimatedPagesOnboarding, maxPagesOnboarding, suggested, suggestedMaxPages));
            }

            // Auto-generate name from URL
            String generatedName = WebsiteDisplayName.suggestedChatbotNameFromUrl(websiteUrl);
            
            // Create chatbot with defaults
            Chatbot chatbot = new Chatbot();
            chatbot.setName(generatedName);
            chatbot.setWebsiteUrl(websiteUrl);
            chatbot.setDescription(""); // Will be filled by website analysis
            chatbot.setPrimaryLanguage("en");
            chatbot.setChristianMessagingEnabled(true); // Pre-configured
            chatbot.setIsActive(true);
            chatbot.setEmbedCode(com.prayer_chat.chatbot.model.Chatbot.generateNewEmbedCode());

            // Create with lock so onboarding is only for first chatbot (max 1)
            Chatbot savedChatbot = chatbotService.createChatbotEnforcingLimit(chatbot, user, 1);
            logger.info("Created chatbot via onboarding: {} for user: {}", 
                LogSanitizer.sanitize(savedChatbot.getName()), LogSanitizer.sanitize(user.getEmail()));

            // Website analysis: run async only so 201 returns quickly; preview page shows "Setting up..." until ready
            try {
                Long savedId = savedChatbot.getId();
                java.util.concurrent.CompletableFuture<List<WebsiteContent>> analysisFuture =
                    websiteAnalysisService.analyzeWebsite(savedChatbot);
                if (analysisFuture != null) {
                    analysisFuture.thenAccept(contents -> {
                        if (contents == null || contents.isEmpty()) return;
                        try {
                            Chatbot c = chatbotRepository.findById(savedId).orElse(null);
                            if (c == null) return;
                            logger.info("Onboarding analysis finished for chatbot {} ({} page(s) saved). Starting indexing.", savedId, contents.size());
                            aiChatbotService.indexWebsiteContent(c);
                            logger.info("Indexed {} pages for onboarding chatbot {}", contents.size(), savedId);
                            List<BibleVerseMatch> matches = christianContentAnalysisService.findRelevantVerses(c, 5, 0.25);
                            if (!matches.isEmpty()) {
                                BibleVerse v = matches.get(0).getVerse();
                                String verseText = v.getReference() + " - \"" + v.getText() + "\"";
                                c.setBibleVerse(verseText);
                                if (c.isJesusTeachingsUnsetInDatabase()) {
                                    c.setJesusTeachingsEnabled(true);
                                }
                                if (c.getChristianMessagingEnabled() == null) c.setChristianMessagingEnabled(true);
                                chatbotRepository.save(c);
                                logger.info("Auto-enabled Christian content for onboarding chatbot {} with verse {}", savedId, v.getReference());
                            }
                        } catch (Exception e) {
                            logger.warn("Onboarding post-analysis step failed for chatbot {}", savedId, e);
                        }
                    });
                }
            } catch (Exception e) {
                logger.warn("Failed to start website analysis for onboarding chatbot {}: {}", 
                    savedChatbot.getId(), e.getMessage());
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(savedChatbot);
        } catch (ChatbotLimitReachedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "Onboarding endpoint is only for creating your first chatbot. Use /api/chatbots to create additional chatbots."
            ));
        } catch (Exception e) {
            logger.error("Error creating chatbot via onboarding", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to create chatbot: " + e.getMessage()));
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

            // Check if user has active subscription (or is in preview mode)
            if (!accessControlService.hasActiveSubscription(user) && !accessControlService.isPreviewMode(user)) {
                logger.warn("User {} attempted to create chatbot without active subscription or preview mode", LogSanitizer.sanitize(user.getEmail()));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "Active subscription or preview mode required to create chatbots."
                ));
            }

            // Check website size limit BEFORE creating chatbot (prevents costs for large sites)
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
            // Defense in depth: only persist allow-listed branding keys/values.
            chatbot.setBrandingConfig(EmbedSecurity.sanitizeBrandingConfig(request.getBrandingConfig()));
            chatbot.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

            // Generate unique embed code
            chatbot.setEmbedCode(com.prayer_chat.chatbot.model.Chatbot.generateNewEmbedCode());

            // Create with pessimistic lock to prevent race condition on chatbot limit
            int maxAllowed = accessControlService.getMaxChatbotsAllowed(user);
            Chatbot savedChatbot = chatbotService.createChatbotEnforcingLimit(chatbot, user, maxAllowed);
            logger.info("Created new chatbot: {} for user: {}", LogSanitizer.sanitize(savedChatbot.getName()), LogSanitizer.sanitize(user.getEmail()));
            return ResponseEntity.status(HttpStatus.CREATED).body(savedChatbot);
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
    
    /**
     * Update a chatbot. Accepts both PUT and PATCH (frontend uses PATCH).
     * Security: same for both methods — requires authentication, verifyAccess (owner + active subscription),
     * path {@code id} only (body cannot change id/owner). Body uses {@link ChatbotUpdatePayload} so PATCH can be
     * partial: omitted properties stay null and must not wipe branding, avatar, or toggles.
     */
    @RequestMapping(value = "/{id}", method = { RequestMethod.PUT, RequestMethod.PATCH })
    public ResponseEntity<Chatbot> updateChatbot(@PathVariable Long id,
                                                 @RequestBody ChatbotUpdatePayload patch,
                                                 @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            User user = currentUser.getUser();
            Optional<Chatbot> chatbotOpt = chatbotRepository.findById(id);

            if (chatbotOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Chatbot chatbot = chatbotOpt.get();

            // Verify access (owner + active subscription)
            ResponseEntity<Void> accessCheck = verifyAccess(user, chatbot);
            if (accessCheck != null) {
                return ResponseEntity.status(accessCheck.getStatusCode()).build();
            }
            if (patch.getName() != null) {
                chatbot.setName(patch.getName());
            }
            if (patch.getWebsiteUrl() != null) {
                chatbot.setWebsiteUrl(patch.getWebsiteUrl());
            }
            if (patch.getDescription() != null) {
                chatbot.setDescription(patch.getDescription());
            }
            if (patch.getPrimaryLanguage() != null) {
                chatbot.setPrimaryLanguage(patch.getPrimaryLanguage());
            }
            if (patch.getSupportedLanguages() != null) {
                chatbot.setSupportedLanguages(patch.getSupportedLanguages());
            }
            if (patch.getCustomPrompt() != null) {
                chatbot.setCustomPrompt(patch.getCustomPrompt());
            }
            if (patch.getIsActive() != null) {
                chatbot.setIsActive(patch.getIsActive());
            }
            if (patch.getWebhookUrl() != null) {
                chatbot.setWebhookUrl(patch.getWebhookUrl());
            }
            if (patch.getWebhookEvents() != null) {
                chatbot.setWebhookEvents(patch.getWebhookEvents());
            }
            if (patch.getQuickReplies() != null) {
                chatbot.setQuickReplies(patch.getQuickReplies());
            }
            if (patch.getBibleVerse() != null) {
                chatbot.setBibleVerse(patch.getBibleVerse());
            }
            if (patch.getChristianMessagingEnabled() != null) {
                chatbot.setChristianMessagingEnabled(patch.getChristianMessagingEnabled());
            }
            if (patch.getJesusTeachingsEnabled() != null) {
                chatbot.setJesusTeachingsEnabled(patch.getJesusTeachingsEnabled());
            }
            if (patch.getBrandingConfig() != null) {
                chatbot.setBrandingConfig(EmbedSecurity.sanitizeBrandingConfig(patch.getBrandingConfig()));
            }
            // Empty string clears avatar; omit property in JSON to leave unchanged.
            if (patch.getAvatarId() != null) {
                chatbot.setAvatarId(EmbedSecurity.validateAvatarId(patch.getAvatarId()));
            }

            Chatbot updatedChatbot = chatbotRepository.save(chatbot);
            logger.info("Updated chatbot: {}", LogSanitizer.sanitize(updatedChatbot.getName()));
            return ResponseEntity.ok(updatedChatbot);
            
        } catch (Exception e) {
            logger.error("Error updating chatbot {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete a chatbot owned by the current user.
     * <p>
     * Website scan and cost limits are tracked per user in {@link WebsiteScanAudit}, which is not cascade-deleted,
     * so deleting a chatbot does not reset daily/monthly scan quotas (delete-and-recreate abuse is prevented).
     * <p>
     * Security: validate id; require same subscription gate as other chatbot APIs; load by {@code id + owner}
     * so a non-owner always gets {@code 404 Not Found} (no confirmation that another user's resource exists).
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteChatbot(@PathVariable Long id,
                                              @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            if (id == null || id < 1) {
                return ResponseEntity.badRequest().build();
            }
            User user = currentUser.getUser();
            if (!hasActiveSubscription(user)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
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
        } catch (Exception e) {
            logger.error("Error deleting chatbot {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Analyze website for a chatbot
     * Enforces cost protection limits for preview mode users:
     * - Website size limit (50 pages max for preview)
     * - Scan frequency limit (1 scan/day for preview)
     * - Cost limit ($5/month for preview)
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
            
            // 1. Check scan frequency limit
            // SECURITY: Use WebsiteScanAudit instead of WebsiteContent to prevent abuse via chatbot deletion
            RateLimitingService.RateLimitResult scanLimitResult = rateLimitingService.checkScanLimit(user);
            if (!scanLimitResult.isAllowed()) {
                logger.warn("User {} attempted to scan website but daily limit reached (current: {}, limit: {}, preview: {})", 
                    LogSanitizer.sanitize(user.getEmail()), scanLimitResult.getCurrent(), scanLimitResult.getLimit(), scanLimitResult.isPreviewMode());
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of(
                    "error", scanLimitResult.getErrorMessage(),
                    "current", scanLimitResult.getCurrent(),
                    "limit", scanLimitResult.getLimit(),
                    "upgradeRequired", scanLimitResult.isUpgradeSuggested()
                ));
            }
            
            // Check if user is in preview mode (for other checks)
            // (intentionally not stored; preview mode is already included in scanLimitResult)
            // 2. Estimate website size BEFORE scanning (prevents costs for large sites)
            int estimatedPages = websiteSizeEstimator.estimateSize(chatbot.getWebsiteUrl());
            int maxPagesForUser = PlanLimits.maxPagesPerScan(accessControlService.getSubscriptionPlan(user));
            if (estimatedPages > maxPagesForUser) {
                Subscription.SubscriptionPlan suggested = PlanLimits.minimumPlanForPages(estimatedPages);
                int suggestedMaxPages = PlanLimits.maxPagesPerScan(suggested);
                logger.warn("User {} attempted to scan website with {} pages (limit: {})", 
                    LogSanitizer.sanitize(user.getEmail()), estimatedPages, maxPagesForUser);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    billingModeService.websiteTooLargePayload(
                        estimatedPages, maxPagesForUser, suggested, suggestedMaxPages));
            }

            // 3. Estimate cost and check plan cost limit (all plans have a monthly cap)
            int estimatedTokens = estimatedPages * 2000;
            java.math.BigDecimal estimatedCost = costTrackingService.calculateWebsiteScanCost(estimatedPages, estimatedTokens);
            try {
                costTrackingService.checkCostLimit(user, estimatedCost);
            } catch (RuntimeException e) {
                logger.warn("User {} attempted to scan website but cost limit would be exceeded: {}", 
                    LogSanitizer.sanitize(user.getEmail()), e.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", e.getMessage(),
                    "estimatedCost", estimatedCost.toString(),
                    "upgradeRequired", billingModeService.shouldSuggestPaidUpgrade()
                ));
            }

            // All checks passed - create audit entry BEFORE starting scan
            // SECURITY: This audit entry persists even if chatbot is deleted, preventing abuse
            WebsiteScanAudit audit = new WebsiteScanAudit(user, chatbot.getWebsiteUrl(), estimatedPages, estimatedCost, chatbot.getId());
            websiteScanAuditRepository.save(audit);

            // Helper: index + Christian content after analysis (used by both sync and async paths)
            java.util.function.Consumer<List<WebsiteContent>> onAnalysisDone = contents -> {
                if (contents == null || contents.isEmpty()) return;
                try {
                    Chatbot c = chatbotRepository.findById(chatbot.getId()).orElse(null);
                    if (c == null) return;
                    logger.info("Website analysis finished for chatbot {} ({} page(s) saved). Starting indexing.", c.getId(), contents.size());
                    aiChatbotService.indexWebsiteContent(c);
                    logger.info("Indexed {} pages for chatbot {} after analysis", contents.size(), c.getId());
                    List<BibleVerseMatch> matches = christianContentAnalysisService.findRelevantVerses(c, 5, 0.25);
                    if (!matches.isEmpty()) {
                        BibleVerse v = matches.get(0).getVerse();
                        String verseText = v.getReference() + " - \"" + v.getText() + "\"";
                        c.setBibleVerse(verseText);
                        if (c.isJesusTeachingsUnsetInDatabase()) {
                            c.setJesusTeachingsEnabled(true);
                        }
                        if (c.getChristianMessagingEnabled() == null) c.setChristianMessagingEnabled(true);
                        chatbotRepository.save(c);
                        logger.info("Auto-enabled Christian content for chatbot {} with verse {}", c.getId(), v.getReference());
                    }
                } catch (Exception e) {
                    logger.warn("Post-analysis indexing/auto-apply failed for chatbot {}", chatbot.getId(), e);
                }
            };

            // Always async: return immediately so UI doesn't block; frontend polls analysis-status until ready
            java.util.concurrent.CompletableFuture<List<WebsiteContent>> analysisFuture =
                websiteAnalysisService.analyzeWebsite(chatbot);
            analysisFuture.thenAccept(onAnalysisDone);

            Map<String, Object> response = Map.of(
                "status", "analysis_started",
                "chatbotId", id,
                "websiteUrl", chatbot.getWebsiteUrl(),
                "estimatedPages", estimatedPages,
                "message", "Website analysis started. Bible verses and \"What Jesus Would Say\" will be enabled automatically when ready."
            );
            logger.info("Started website analysis (async) for chatbot: {} (estimated {} pages)", 
                LogSanitizer.sanitize(chatbot.getName()), estimatedPages);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error starting website analysis for chatbot {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "An error occurred while starting website analysis. Please try again."
            ));
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
    public ResponseEntity<?> getEmbedCode(@PathVariable Long id,
                                                            @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
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
            
            // Check if user can access integration script (requires paid subscription)
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
            
        } catch (Exception e) {
            logger.error("Error generating embed code for chatbot {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    private static final String DEFAULT_BASE_URL = "https://chatbot-java-spring-ai.onrender.com";
    /** Deprecated backend host; redirect embed to current default so old env vars don't leak into snippets. */
    private static final String DEPRECATED_BASE_URL = "https://chatbot-backend-4mp4.onrender.com";

    /**
     * Generate embed code for chatbot.
     * Security: baseUrl is from configuration only (never user input — SSRF safe). It is validated
     * and escaped so the generated HTML/JS cannot break out of the script context (XSS prevention).
     * Error messages in onerror/onload use literal strings only (no user input); embedChatbotId
     * is the numeric chatbot id. No credentials or secrets are included in the snippet.
     */
    private String generateEmbedCode(Chatbot chatbot) {
        String configured = baseUrl == null ? "" : baseUrl.trim();
        if (DEPRECATED_BASE_URL.equals(configured) || DEPRECATED_BASE_URL.equals(configured.replaceAll("/$", ""))) {
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

    // ============================================================================
    // NEW FEATURE ENDPOINTS
    // ============================================================================

    /**
     * NEW FEATURE: Export conversation to JSON
     */
    @GetMapping("/conversations/{conversationId}/export/json")
    public ResponseEntity<String> exportConversationJson(@PathVariable Long conversationId,
                                                         @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            if (conversationRepository == null) {
                logger.error("Conversation repository not configured for conversation export");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
            User user = currentUser.getUser();
            Optional<Conversation> conversationOpt =
                conversationRepository.findByIdAndChatbot_Owner_Id(conversationId, user.getId());
            if (conversationOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

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
    public ResponseEntity<String> exportConversationCsv(@PathVariable Long conversationId,
                                                        @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            User user = currentUser.getUser();
            Optional<Conversation> conversationOpt =
                conversationRepository.findByIdAndChatbot_Owner_Id(conversationId, user.getId());
            if (conversationOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

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
     * CHRISTIAN MESSAGING FEATURE: Analyze website content and find relevant Bible verses using AI semantic matching
     * This replaces the old keyword-based approach
     */
    @PostMapping("/{id}/analyze-christian-content")
    public ResponseEntity<?> analyzeChristianContent(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "20") int maxVerses,
            @RequestParam(required = false, defaultValue = "0.5") double similarityThreshold,
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

            // Christian Content Analysis requires previously crawled website content
            String websiteContent = websiteAnalysisService.getAnalyzedContent(chatbot);
            if (websiteContent == null || websiteContent.trim().isEmpty()) {
                logger.warn("Christian content analysis requested but no website content for chatbot {}", id);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    Map.of(
                        "error", "No website content available. Run Website Analysis for this chatbot first (use \"Analyze website\" or re-scan), then try Christian Content Analysis again.",
                        "code", "NO_WEBSITE_CONTENT"
                    )
                );
            }

            // Find relevant verses using AI semantic matching
            var matches = christianContentAnalysisService.findRelevantVerses(
                chatbot, maxVerses, similarityThreshold
            );

            // Get total verse count for context
            long totalVerses = christianContentAnalysisService.getVerseCount();

            // Build response DTO
            ChristianContentAnalysis analysis = new ChristianContentAnalysis(
                chatbot.getId(),
                chatbot.getWebsiteUrl(),
                matches,
                (int) totalVerses
            );

            logger.info("Analyzed Christian content for chatbot {}: found {} relevant verses", 
                id, matches.size());
            return ResponseEntity.ok(analysis);

        } catch (Exception e) {
            logger.error("Error analyzing Christian content for chatbot {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Preview Jesus's teachings relevant to the chatbot's website
     * Shows what teachings would be used if "What Jesus Would Say" feature is enabled
     */
    @PostMapping("/{id}/preview-jesus-teachings")
    public ResponseEntity<Map<String, Object>> previewJesusTeachings(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "5") int maxTeachings,
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

            // Get website content for context
            String websiteContent = websiteAnalysisService.getAnalyzedContent(chatbot);
            if (websiteContent == null || websiteContent.trim().isEmpty()) {
                websiteContent = chatbot.getDescription() != null ? chatbot.getDescription() : "";
            }

            // Find relevant Jesus teachings
            List<JesusTeaching> teachings = jesusTeachingsService.findRelevantTeachings(
                    websiteContent, maxTeachings, 0.4); // Lower threshold for preview

            // Get total count of Jesus verses
            long totalJesusVerses = jesusVersesTaggingService.getJesusVersesCount();

            // Build response
            Map<String, Object> response = Map.of(
                    "chatbotId", id.toString(),
                    "websiteUrl", chatbot.getWebsiteUrl() != null ? chatbot.getWebsiteUrl() : "",
                    "topTeachings", teachings.stream()
                            .map(t -> Map.of(
                                    "reference", t.getReference(),
                                    "text", t.getText(),
                                    "similarity", String.format("%.2f", t.getSimilarity())
                            ))
                            .toList(),
                    "totalJesusVerses", totalJesusVerses
            );

            logger.info("Previewed Jesus teachings for chatbot {}: found {} teachings", 
                    id, teachings.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error previewing Jesus teachings for chatbot {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * LEGACY ENDPOINT: Suggest Bible verse based on website topic (keyword-based)
     * @deprecated Use /analyze-christian-content instead for AI-powered semantic matching
     */
    @Deprecated
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

            // Suggest Bible verse (legacy keyword-based approach)
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
