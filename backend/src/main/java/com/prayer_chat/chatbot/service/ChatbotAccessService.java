package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.exception.ForbiddenException;
import com.prayer_chat.chatbot.exception.ResourceNotFoundException;
import com.prayer_chat.chatbot.exception.UnauthorizedException;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.security.CustomOAuth2User;
import com.prayer_chat.chatbot.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Centralizes chatbot authorization checks so controllers stay thin and consistent.
 */
@Service
public class ChatbotAccessService {

    private static final Logger logger = LoggerFactory.getLogger(ChatbotAccessService.class);

    private final AccessControlService accessControlService;
    private final ChatbotRepository chatbotRepository;

    public ChatbotAccessService(AccessControlService accessControlService,
                                ChatbotRepository chatbotRepository) {
        this.accessControlService = accessControlService;
        this.chatbotRepository = chatbotRepository;
    }

    public User requireAuthenticated(CustomOAuth2User currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException();
        }
        return currentUser.getUser();
    }

    public boolean hasActiveSubscription(User user) {
        return accessControlService.hasActiveSubscription(user)
            || accessControlService.isPreviewMode(user);
    }

    public void requireActiveSubscription(User user) {
        if (!hasActiveSubscription(user)) {
            logger.warn("User {} attempted action without active subscription",
                LogSanitizer.sanitize(user.getEmail()));
            throw new ForbiddenException("Active subscription or preview mode required");
        }
    }

    public boolean isOwner(User user, Chatbot chatbot) {
        return chatbot.getOwner() != null && chatbot.getOwner().getId().equals(user.getId());
    }

    public void requireOwner(User user, Chatbot chatbot) {
        if (!isOwner(user, chatbot)) {
            logger.warn("User {} attempted to access chatbot {} without ownership",
                LogSanitizer.sanitize(user.getEmail()), chatbot.getId());
            throw new ForbiddenException("Access denied");
        }
    }

    public Chatbot requireOwnedChatbot(User user, Long chatbotId) {
        if (chatbotId == null || chatbotId < 0) {
            throw new IllegalArgumentException("Invalid chatbot ID");
        }
        requireActiveSubscription(user);
        Chatbot chatbot = chatbotRepository.findById(chatbotId)
            .orElseThrow(() -> ResourceNotFoundException.forResource("Chatbot", chatbotId));
        requireOwner(user, chatbot);
        return chatbot;
    }
}
