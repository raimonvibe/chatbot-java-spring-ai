package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.config.PlanLimits;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates website analysis, indexing, and analysis status for chatbots.
 */
@Service
public class ChatbotWebsiteAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(ChatbotWebsiteAnalysisService.class);

    private final WebsiteAnalysisService websiteAnalysisService;
    private final AiChatbotService aiChatbotService;
    private final RateLimitingService rateLimitingService;
    private final WebsiteSizeEstimator websiteSizeEstimator;
    private final AccessControlService accessControlService;
    private final CostTrackingService costTrackingService;
    private final BillingModeService billingModeService;
    private final WebsiteScanAuditRepository websiteScanAuditRepository;
    private final ChatbotRepository chatbotRepository;
    private final ChristianContentAnalysisService christianContentAnalysisService;

    public ChatbotWebsiteAnalysisService(WebsiteAnalysisService websiteAnalysisService,
                                         AiChatbotService aiChatbotService,
                                         RateLimitingService rateLimitingService,
                                         WebsiteSizeEstimator websiteSizeEstimator,
                                         AccessControlService accessControlService,
                                         CostTrackingService costTrackingService,
                                         BillingModeService billingModeService,
                                         WebsiteScanAuditRepository websiteScanAuditRepository,
                                         ChatbotRepository chatbotRepository,
                                         ChristianContentAnalysisService christianContentAnalysisService) {
        this.websiteAnalysisService = websiteAnalysisService;
        this.aiChatbotService = aiChatbotService;
        this.rateLimitingService = rateLimitingService;
        this.websiteSizeEstimator = websiteSizeEstimator;
        this.accessControlService = accessControlService;
        this.costTrackingService = costTrackingService;
        this.billingModeService = billingModeService;
        this.websiteScanAuditRepository = websiteScanAuditRepository;
        this.chatbotRepository = chatbotRepository;
        this.christianContentAnalysisService = christianContentAnalysisService;
    }

    public Map<String, Object> getAnalysisStatus(Chatbot chatbot) {
        return websiteAnalysisService.getAnalysisStatus(chatbot);
    }

    public Map<String, Object> indexContent(Chatbot chatbot) {
        aiChatbotService.indexWebsiteContent(chatbot);
        logger.info("Completed content indexing for chatbot: {}", LogSanitizer.sanitize(chatbot.getName()));
        return Map.of(
            "status", "indexing_completed",
            "chatbotId", chatbot.getId(),
            "message", "Website content has been indexed and is ready for chatbot interactions."
        );
    }

    @Transactional
    public ResponseEntity<Map<String, Object>> startWebsiteAnalysis(Long id, User user, Chatbot chatbot) {
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

        int estimatedPages = websiteSizeEstimator.estimateSize(chatbot.getWebsiteUrl());
        int maxPagesForUser = PlanLimits.maxPagesPerScan(accessControlService.getSubscriptionPlan(user));
        if (estimatedPages > maxPagesForUser) {
            Subscription.SubscriptionPlan suggested = PlanLimits.minimumPlanForPages(estimatedPages);
            int suggestedMaxPages = PlanLimits.maxPagesPerScan(suggested);
            logger.warn("User {} attempted to scan website with {} pages (limit: {})",
                LogSanitizer.sanitize(user.getEmail()), estimatedPages, maxPagesForUser);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                billingModeService.websiteTooLargePayload(estimatedPages, maxPagesForUser, suggested, suggestedMaxPages));
        }

        int estimatedTokens = estimatedPages * 2000;
        java.math.BigDecimal estimatedCost = costTrackingService.calculateWebsiteScanCost(estimatedPages, estimatedTokens);
        try {
            costTrackingService.trackWebsiteScanCost(user, estimatedPages, estimatedTokens);
        } catch (RuntimeException e) {
            logger.warn("User {} attempted to scan website but cost limit would be exceeded: {}",
                LogSanitizer.sanitize(user.getEmail()), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", e.getMessage(),
                "estimatedCost", estimatedCost.toString(),
                "upgradeRequired", billingModeService.shouldSuggestPaidUpgrade()
            ));
        }

        WebsiteScanAudit audit = new WebsiteScanAudit(user, chatbot.getWebsiteUrl(), estimatedPages, estimatedCost, chatbot.getId());
        websiteScanAuditRepository.save(audit);

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

        CompletableFuture<List<WebsiteContent>> analysisFuture = websiteAnalysisService.analyzeWebsite(chatbot);
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
    }
}
