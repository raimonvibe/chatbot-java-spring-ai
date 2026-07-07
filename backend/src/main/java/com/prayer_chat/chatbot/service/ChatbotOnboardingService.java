package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.config.PlanLimits;
import com.prayer_chat.chatbot.exception.ChatbotLimitReachedException;
import com.prayer_chat.chatbot.exception.ForbiddenException;
import com.prayer_chat.chatbot.exception.WebsiteTooLargeException;
import com.prayer_chat.chatbot.model.BibleVerse;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.model.WebsiteContent;
import com.prayer_chat.chatbot.model.WebsiteScanAudit;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.repository.WebsiteScanAuditRepository;
import com.prayer_chat.chatbot.service.ChristianContentAnalysisService.BibleVerseMatch;
import com.prayer_chat.chatbot.util.LogSanitizer;
import com.prayer_chat.chatbot.util.WebsiteDisplayName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates first-chatbot onboarding: validation, creation, async crawl, indexing, and Christian content setup.
 */
@Service
public class ChatbotOnboardingService {

    private static final Logger logger = LoggerFactory.getLogger(ChatbotOnboardingService.class);

    private final ChatbotRepository chatbotRepository;
    private final ChatbotService chatbotService;
    private final WebsiteAnalysisService websiteAnalysisService;
    private final AiChatbotService aiChatbotService;
    private final ChristianContentAnalysisService christianContentAnalysisService;
    private final WebsiteScanAuditRepository websiteScanAuditRepository;
    private final AccessControlService accessControlService;
    private final RateLimitingService rateLimitingService;
    private final CostTrackingService costTrackingService;
    private final WebsiteSizeEstimator websiteSizeEstimator;
    private final BillingModeService billingModeService;
    private final UrlValidationService urlValidationService;

    public ChatbotOnboardingService(ChatbotRepository chatbotRepository,
                                    ChatbotService chatbotService,
                                    WebsiteAnalysisService websiteAnalysisService,
                                    AiChatbotService aiChatbotService,
                                    ChristianContentAnalysisService christianContentAnalysisService,
                                    WebsiteScanAuditRepository websiteScanAuditRepository,
                                    AccessControlService accessControlService,
                                    RateLimitingService rateLimitingService,
                                    CostTrackingService costTrackingService,
                                    WebsiteSizeEstimator websiteSizeEstimator,
                                    BillingModeService billingModeService,
                                    UrlValidationService urlValidationService) {
        this.chatbotRepository = chatbotRepository;
        this.chatbotService = chatbotService;
        this.websiteAnalysisService = websiteAnalysisService;
        this.aiChatbotService = aiChatbotService;
        this.christianContentAnalysisService = christianContentAnalysisService;
        this.websiteScanAuditRepository = websiteScanAuditRepository;
        this.accessControlService = accessControlService;
        this.rateLimitingService = rateLimitingService;
        this.costTrackingService = costTrackingService;
        this.websiteSizeEstimator = websiteSizeEstimator;
        this.billingModeService = billingModeService;
        this.urlValidationService = urlValidationService;
    }

    @Transactional
    public Chatbot createFromWebsiteUrl(User user, String rawWebsiteUrl) {
        Optional<String> canonicalWebsite = urlValidationService.completeAndValidate(rawWebsiteUrl);
        if (canonicalWebsite.isEmpty()) {
            throw new IllegalArgumentException("Invalid or unsafe website URL");
        }
        String websiteUrl = canonicalWebsite.get();

        Long currentChatbotCount = chatbotRepository.countByOwner(user.getId());
        if (currentChatbotCount > 0) {
            throw new ForbiddenException(
                "Onboarding endpoint is only for creating your first chatbot. Use /api/chatbots to create additional chatbots."
            );
        }

        if (!accessControlService.hasActiveSubscription(user) && !accessControlService.isPreviewMode(user)) {
            throw new ForbiddenException("Active subscription or preview mode required to create chatbots.");
        }

        int estimatedPages = websiteSizeEstimator.estimateSize(websiteUrl);
        int maxPages = PlanLimits.maxPagesPerScan(accessControlService.getSubscriptionPlan(user));
        if (estimatedPages > maxPages) {
            Subscription.SubscriptionPlan suggested = PlanLimits.minimumPlanForPages(estimatedPages);
            int suggestedMaxPages = PlanLimits.maxPagesPerScan(suggested);
            throw new WebsiteTooLargeException(
                billingModeService, estimatedPages, maxPages, suggested, suggestedMaxPages);
        }

        RateLimitingService.RateLimitResult scanLimitResult = rateLimitingService.checkScanLimit(user);
        if (!scanLimitResult.isAllowed()) {
            throw new ForbiddenException(scanLimitResult.getErrorMessage());
        }

        int estimatedTokens = estimatedPages * 2000;
        java.math.BigDecimal estimatedCost = costTrackingService.calculateWebsiteScanCost(estimatedPages, estimatedTokens);
        try {
            costTrackingService.trackWebsiteScanCost(user, estimatedPages, estimatedTokens);
        } catch (RuntimeException e) {
            throw new ForbiddenException(e.getMessage());
        }

        Chatbot chatbot = new Chatbot();
        chatbot.setName(WebsiteDisplayName.suggestedChatbotNameFromUrl(websiteUrl));
        chatbot.setWebsiteUrl(websiteUrl);
        chatbot.setDescription("");
        chatbot.setPrimaryLanguage("en");
        chatbot.setChristianMessagingEnabled(true);
        chatbot.setIsActive(true);
        chatbot.setEmbedCode(Chatbot.generateNewEmbedCode());

        Chatbot savedChatbot;
        try {
            savedChatbot = chatbotService.createChatbotEnforcingLimit(chatbot, user, 1);
        } catch (ChatbotLimitReachedException e) {
            throw new ForbiddenException(
                "Onboarding endpoint is only for creating your first chatbot. Use /api/chatbots to create additional chatbots."
            );
        }

        logger.info("Created chatbot via onboarding: {} for user: {}",
            LogSanitizer.sanitize(savedChatbot.getName()), LogSanitizer.sanitize(user.getEmail()));

        WebsiteScanAudit audit = new WebsiteScanAudit(user, websiteUrl, estimatedPages, estimatedCost, savedChatbot.getId());
        websiteScanAuditRepository.save(audit);

        startPostCreateAnalysis(savedChatbot);
        return savedChatbot;
    }

    /**
     * Validates website size before creation. Returns error payload map if too large, empty if OK.
     */
    public Optional<Map<String, Object>> validateWebsiteSizeForUser(User user, String websiteUrl) {
        if (websiteUrl == null || websiteUrl.trim().isEmpty()) {
            return Optional.empty();
        }
        String normalized = websiteUrl;
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        int estimatedPages = websiteSizeEstimator.estimateSize(normalized);
        int maxPages = PlanLimits.maxPagesPerScan(accessControlService.getSubscriptionPlan(user));
        if (estimatedPages > maxPages) {
            Subscription.SubscriptionPlan suggested = PlanLimits.minimumPlanForPages(estimatedPages);
            int suggestedMaxPages = PlanLimits.maxPagesPerScan(suggested);
            return Optional.of(billingModeService.websiteTooLargePayload(
                estimatedPages, maxPages, suggested, suggestedMaxPages));
        }
        return Optional.empty();
    }

    public void startPostCreateAnalysis(Chatbot savedChatbot) {
        try {
            Long savedId = savedChatbot.getId();
            CompletableFuture<List<WebsiteContent>> analysisFuture =
                websiteAnalysisService.analyzeWebsite(savedChatbot);
            if (analysisFuture != null) {
                analysisFuture.thenAccept(contents -> runPostAnalysisSteps(savedId, contents));
            }
        } catch (Exception e) {
            logger.warn("Failed to start website analysis for onboarding chatbot {}: {}",
                savedChatbot.getId(), e.getMessage());
        }
    }

    private void runPostAnalysisSteps(Long savedId, List<WebsiteContent> contents) {
        if (contents == null || contents.isEmpty()) return;
        try {
            Chatbot c = chatbotRepository.findById(savedId).orElse(null);
            if (c == null) return;
            logger.info("Onboarding analysis finished for chatbot {} ({} page(s) saved). Starting indexing.",
                savedId, contents.size());
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
                logger.info("Auto-enabled Christian content for onboarding chatbot {} with verse {}",
                    savedId, v.getReference());
            }
        } catch (Exception e) {
            logger.warn("Onboarding post-analysis step failed for chatbot {}", savedId, e);
        }
    }
}
