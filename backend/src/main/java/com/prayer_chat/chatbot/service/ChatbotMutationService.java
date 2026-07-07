package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.dto.ChatbotUpdatePayload;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.util.EmbedSecurity;
import com.prayer_chat.chatbot.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies validated partial updates to chatbots (PATCH/PUT body → entity).
 * Keeps field mapping and sanitization out of the controller layer.
 */
@Service
public class ChatbotMutationService {

    private static final Logger logger = LoggerFactory.getLogger(ChatbotMutationService.class);

    private final ChatbotRepository chatbotRepository;
    private final UrlValidationService urlValidationService;

    public ChatbotMutationService(ChatbotRepository chatbotRepository,
                                  UrlValidationService urlValidationService) {
        this.chatbotRepository = chatbotRepository;
        this.urlValidationService = urlValidationService;
    }

    @Transactional
    public Chatbot applyPatch(Chatbot chatbot, ChatbotUpdatePayload patch) {
        if (patch.getName() != null) {
            chatbot.setName(patch.getName());
        }
        if (patch.getWebsiteUrl() != null) {
            String websiteUrl = patch.getWebsiteUrl().trim();
            if (!websiteUrl.isEmpty() && !urlValidationService.isValidAndSafe(websiteUrl)) {
                throw new IllegalArgumentException("Invalid or unsafe website URL");
            }
            chatbot.setWebsiteUrl(websiteUrl.isEmpty() ? null : websiteUrl);
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
            String webhookUrl = patch.getWebhookUrl().trim();
            if (!webhookUrl.isEmpty() && !urlValidationService.isValidAndSafe(webhookUrl)) {
                throw new IllegalArgumentException("Invalid or unsafe webhook URL");
            }
            chatbot.setWebhookUrl(webhookUrl.isEmpty() ? null : webhookUrl);
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
        if (patch.getAvatarId() != null) {
            chatbot.setAvatarId(EmbedSecurity.validateAvatarId(patch.getAvatarId()));
        }

        Chatbot saved = chatbotRepository.save(chatbot);
        logger.info("Updated chatbot: {}", LogSanitizer.sanitize(saved.getName()));
        return saved;
    }
}
