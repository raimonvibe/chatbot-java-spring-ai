package com.prayer_chat.chatbot.controller;

import com.prayer_chat.chatbot.model.Conversation;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.ConversationRepository;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
import com.prayer_chat.chatbot.service.ChatbotAccessService;
import com.prayer_chat.chatbot.service.ConversationExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Export endpoints for chatbot conversations (JSON/CSV).
 */
@RestController
@RequestMapping("/api/chatbots")
public class ChatbotExportController {

    private static final Logger logger = LoggerFactory.getLogger(ChatbotExportController.class);

    private final ConversationRepository conversationRepository;
    private final ConversationExportService conversationExportService;
    private final ChatbotAccessService chatbotAccessService;

    public ChatbotExportController(ConversationRepository conversationRepository,
                                   ConversationExportService conversationExportService,
                                   ChatbotAccessService chatbotAccessService) {
        this.conversationRepository = conversationRepository;
        this.conversationExportService = conversationExportService;
        this.chatbotAccessService = chatbotAccessService;
    }

    @GetMapping("/conversations/{conversationId}/export/json")
    public ResponseEntity<String> exportConversationJson(@PathVariable Long conversationId,
                                                         @AuthenticationPrincipal CustomOAuth2User currentUser) {
        User user = chatbotAccessService.requireAuthenticated(currentUser);
        Optional<Conversation> conversationOpt =
            conversationRepository.findByIdAndChatbot_Owner_Id(conversationId, user.getId());
        if (conversationOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        try {
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

    @GetMapping("/conversations/{conversationId}/export/csv")
    public ResponseEntity<String> exportConversationCsv(@PathVariable Long conversationId,
                                                       @AuthenticationPrincipal CustomOAuth2User currentUser) {
        User user = chatbotAccessService.requireAuthenticated(currentUser);
        Optional<Conversation> conversationOpt =
            conversationRepository.findByIdAndChatbot_Owner_Id(conversationId, user.getId());
        if (conversationOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        try {
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

    @GetMapping("/{id}/export/json")
    public ResponseEntity<String> exportChatbotConversationsJson(@PathVariable Long id,
                                                                 @AuthenticationPrincipal CustomOAuth2User currentUser) {
        User user = chatbotAccessService.requireAuthenticated(currentUser);
        chatbotAccessService.requireOwnedChatbot(user, id);
        try {
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

    @GetMapping("/{id}/export/csv")
    public ResponseEntity<String> exportChatbotConversationsCsv(@PathVariable Long id,
                                                                @AuthenticationPrincipal CustomOAuth2User currentUser) {
        User user = chatbotAccessService.requireAuthenticated(currentUser);
        chatbotAccessService.requireOwnedChatbot(user, id);
        try {
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
}
