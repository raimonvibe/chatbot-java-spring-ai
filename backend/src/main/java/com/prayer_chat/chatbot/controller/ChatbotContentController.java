package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.dto.ChristianContentAnalysis;
import com.prayer_chat.chatbot.dto.JesusTeaching;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
import com.prayer_chat.chatbot.service.BibleVerseService;
import com.prayer_chat.chatbot.service.ChatbotAccessService;
import com.prayer_chat.chatbot.service.ChristianContentAnalysisService;
import com.prayer_chat.chatbot.service.JesusTeachingsService;
import com.prayer_chat.chatbot.service.JesusVersesTaggingService;
import com.prayer_chat.chatbot.service.WebsiteAnalysisService;
import com.prayer_chat.chatbot.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Christian content, quick replies, and Jesus teachings endpoints for chatbots.
 */
@RestController
@RequestMapping("/api/chatbots")
public class ChatbotContentController {

    private static final Logger logger = LoggerFactory.getLogger(ChatbotContentController.class);

    private final ChatbotRepository chatbotRepository;
    private final ChatbotAccessService chatbotAccessService;
    private final WebsiteAnalysisService websiteAnalysisService;
    private final ChristianContentAnalysisService christianContentAnalysisService;
    private final JesusTeachingsService jesusTeachingsService;
    private final JesusVersesTaggingService jesusVersesTaggingService;
    private final BibleVerseService bibleVerseService;

    public ChatbotContentController(ChatbotRepository chatbotRepository,
                                    ChatbotAccessService chatbotAccessService,
                                    WebsiteAnalysisService websiteAnalysisService,
                                    ChristianContentAnalysisService christianContentAnalysisService,
                                    JesusTeachingsService jesusTeachingsService,
                                    JesusVersesTaggingService jesusVersesTaggingService,
                                    BibleVerseService bibleVerseService) {
        this.chatbotRepository = chatbotRepository;
        this.chatbotAccessService = chatbotAccessService;
        this.websiteAnalysisService = websiteAnalysisService;
        this.christianContentAnalysisService = christianContentAnalysisService;
        this.jesusTeachingsService = jesusTeachingsService;
        this.jesusVersesTaggingService = jesusVersesTaggingService;
        this.bibleVerseService = bibleVerseService;
    }

    @GetMapping("/{id}/quick-replies")
    public ResponseEntity<String> getQuickReplies(@PathVariable Long id,
                                                  @AuthenticationPrincipal CustomOAuth2User currentUser) {
        User user = chatbotAccessService.requireAuthenticated(currentUser);
        Chatbot chatbot = chatbotAccessService.requireOwnedChatbot(user, id);
        String quickReplies = chatbot.getQuickReplies();
        return ResponseEntity.ok()
            .header("Content-Type", "application/json")
            .body(quickReplies != null ? quickReplies : "[]");
    }

    @PostMapping("/{id}/analyze-christian-content")
    public ResponseEntity<?> analyzeChristianContent(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "20") int maxVerses,
            @RequestParam(required = false, defaultValue = "0.5") double similarityThreshold,
            @AuthenticationPrincipal CustomOAuth2User currentUser) {
        User user = chatbotAccessService.requireAuthenticated(currentUser);
        maxVerses = Math.min(Math.max(1, maxVerses), 100);
        similarityThreshold = Math.min(Math.max(0.0, similarityThreshold), 1.0);
        Chatbot chatbot = chatbotAccessService.requireOwnedChatbot(user, id);

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

        var matches = christianContentAnalysisService.findRelevantVerses(chatbot, maxVerses, similarityThreshold);
        long totalVerses = christianContentAnalysisService.getVerseCount();
        ChristianContentAnalysis analysis = new ChristianContentAnalysis(
            chatbot.getId(),
            chatbot.getWebsiteUrl(),
            matches,
            (int) totalVerses
        );
        logger.info("Analyzed Christian content for chatbot {}: found {} relevant verses", id, matches.size());
        return ResponseEntity.ok(analysis);
    }

    @PostMapping("/{id}/preview-jesus-teachings")
    public ResponseEntity<Map<String, Object>> previewJesusTeachings(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "5") int maxTeachings,
            @AuthenticationPrincipal CustomOAuth2User currentUser) {
        User user = chatbotAccessService.requireAuthenticated(currentUser);
        maxTeachings = Math.min(Math.max(1, maxTeachings), 20);
        Chatbot chatbot = chatbotAccessService.requireOwnedChatbot(user, id);

        String websiteContent = websiteAnalysisService.getAnalyzedContent(chatbot);
        if (websiteContent == null || websiteContent.trim().isEmpty()) {
            websiteContent = chatbot.getDescription() != null ? chatbot.getDescription() : "";
        }

        List<JesusTeaching> teachings = jesusTeachingsService.findRelevantTeachings(websiteContent, maxTeachings, 0.4);
        long totalJesusVerses = jesusVersesTaggingService.getJesusVersesCount();

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
        logger.info("Previewed Jesus teachings for chatbot {}: found {} teachings", id, teachings.size());
        return ResponseEntity.ok(response);
    }

    @Deprecated
    @PostMapping("/{id}/suggest-bible-verse")
    public ResponseEntity<Map<String, String>> suggestBibleVerse(@PathVariable Long id,
                                                                 @AuthenticationPrincipal CustomOAuth2User currentUser) {
        User user = chatbotAccessService.requireAuthenticated(currentUser);
        Chatbot chatbot = chatbotAccessService.requireOwnedChatbot(user, id);

        String websiteContent = websiteAnalysisService.getAnalyzedContent(chatbot);
        String suggestedVerse = bibleVerseService.suggestBibleVerse(
            chatbot.getWebsiteUrl(),
            chatbot.getDescription(),
            websiteContent
        );

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
    }
}
