package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
import com.prayer_chat.chatbot.service.ChatbotAccessService;
import com.prayer_chat.chatbot.service.ChatbotWebsiteAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Website analysis and indexing endpoints for chatbots.
 */
@RestController
@RequestMapping("/api/chatbots")
public class ChatbotAnalysisController {

    private static final Logger logger = LoggerFactory.getLogger(ChatbotAnalysisController.class);

    private final ChatbotAccessService chatbotAccessService;
    private final ChatbotWebsiteAnalysisService chatbotWebsiteAnalysisService;

    public ChatbotAnalysisController(ChatbotAccessService chatbotAccessService,
                                     ChatbotWebsiteAnalysisService chatbotWebsiteAnalysisService) {
        this.chatbotAccessService = chatbotAccessService;
        this.chatbotWebsiteAnalysisService = chatbotWebsiteAnalysisService;
    }

    @GetMapping("/{id}/analysis-status")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getAnalysisStatus(@PathVariable Long id,
                                                                @AuthenticationPrincipal CustomOAuth2User currentUser) {
        if (id == null || id < 0) {
            return ResponseEntity.badRequest().build();
        }
        User user = chatbotAccessService.requireAuthenticated(currentUser);
        Chatbot chatbot = chatbotAccessService.requireOwnedChatbot(user, id);
        return ResponseEntity.ok(chatbotWebsiteAnalysisService.getAnalysisStatus(chatbot));
    }

    @PostMapping("/{id}/analyze")
    @Transactional
    public ResponseEntity<Map<String, Object>> analyzeWebsite(@PathVariable Long id,
                                                              @AuthenticationPrincipal CustomOAuth2User currentUser) {
        User user = chatbotAccessService.requireAuthenticated(currentUser);
        Chatbot chatbot = chatbotAccessService.requireOwnedChatbot(user, id);
        return chatbotWebsiteAnalysisService.startWebsiteAnalysis(id, user, chatbot);
    }

    @PostMapping("/{id}/index")
    public ResponseEntity<Map<String, Object>> indexContent(@PathVariable Long id,
                                                            @AuthenticationPrincipal CustomOAuth2User currentUser) {
        try {
            User user = chatbotAccessService.requireAuthenticated(currentUser);
            Chatbot chatbot = chatbotAccessService.requireOwnedChatbot(user, id);
            return ResponseEntity.ok(chatbotWebsiteAnalysisService.indexContent(chatbot));
        } catch (Exception e) {
            logger.error("Error indexing content for chatbot {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
