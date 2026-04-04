package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.exception.ChatbotLimitReachedException;
import com.prayer_chat.chatbot.model.AuditLog;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.repository.UserRepository;
import com.prayer_chat.chatbot.util.XssSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing chatbot operations with security checks
 * Handles CRUD operations, authorization, URL validation, and XSS protection
 */
@Service
public class ChatbotService {

    private static final Logger logger = LoggerFactory.getLogger(ChatbotService.class);

    @Autowired
    private ChatbotRepository chatbotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UrlValidationService urlValidationService;

    @Autowired
    private AuditService auditService;

    @Autowired(required = false)
    private AiChatbotService aiChatbotService;

    @Autowired
    private XssSanitizer xssSanitizer;

    /**
     * Create a new chatbot with security validations
     *
     * @param chatbot the chatbot to create
     * @param user the owner of the chatbot
     * @return the created chatbot
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public Chatbot createChatbot(Chatbot chatbot, User user) {
        return doCreateChatbot(chatbot, user);
    }

    /**
     * Create a new chatbot while enforcing a per-user limit under pessimistic lock.
     * Prevents race conditions where concurrent requests could exceed the chatbot quota.
     *
     * @param chatbot the chatbot to create
     * @param user the owner of the chatbot
     * @param maxAllowed maximum number of chatbots allowed for this user (e.g. from AccessControlService)
     * @return the created chatbot
     * @throws ChatbotLimitReachedException if the user has already reached maxAllowed chatbots
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public Chatbot createChatbotEnforcingLimit(Chatbot chatbot, User user, int maxAllowed) {
        // Lock user row to serialize concurrent creation attempts for this user
        User lockedUser = userRepository.findByIdWithLock(user.getId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + user.getId()));

        long currentCount = chatbotRepository.countByOwner(lockedUser.getId());
        if (currentCount >= maxAllowed) {
            throw new ChatbotLimitReachedException(maxAllowed, currentCount);
        }

        return doCreateChatbot(chatbot, lockedUser);
    }

    /**
     * Internal create: validation, sanitization, save, audit. No locking.
     */
    private Chatbot doCreateChatbot(Chatbot chatbot, User user) {
        logger.debug("Creating chatbot for user: {}", user.getId());

        validateRequiredFields(chatbot);

        if (!urlValidationService.isValid(chatbot.getWebsiteUrl())) {
            logger.warn("Invalid or unsafe website URL: {}", chatbot.getWebsiteUrl());
            throw new IllegalArgumentException("Invalid or unsafe website URL");
        }

        sanitizeChatbotFields(chatbot);
        chatbot.setOwner(user);

        Chatbot savedChatbot = chatbotRepository.save(chatbot);

        auditService.log(
            AuditLog.EventType.CHATBOT_CREATED,
            AuditLog.Severity.INFO,
            "Chatbot created: " + savedChatbot.getName(),
            user,
            null,
            null
        );

        logger.info("Created chatbot: {} for user: {}", savedChatbot.getId(), user.getId());
        return savedChatbot;
    }

    /**
     * Update an existing chatbot with authorization check
     *
     * @param id the chatbot ID
     * @param updates the updated chatbot data
     * @param user the user requesting the update
     * @return the updated chatbot
     * @throws SecurityException if user is not authorized
     * @throws IllegalArgumentException if chatbot not found or validation fails
     */
    @Transactional
    public Chatbot updateChatbot(Long id, Chatbot updates, User user) {
        logger.debug("Updating chatbot {} for user: {}", id, user.getId());

        // Find the chatbot
        Chatbot chatbot = chatbotRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Chatbot not found: " + id));

        // Check authorization
        if (!isOwner(user, chatbot)) {
            logger.warn("User {} attempted to update chatbot {} without authorization", user.getId(), id);
            throw new SecurityException("You are not authorized to update this chatbot");
        }

        // Validate URL if it's being updated
        if (updates.getWebsiteUrl() != null && !updates.getWebsiteUrl().equals(chatbot.getWebsiteUrl())) {
            if (!urlValidationService.isValid(updates.getWebsiteUrl())) {
                logger.warn("Invalid or unsafe website URL: {}", updates.getWebsiteUrl());
                throw new IllegalArgumentException("Invalid or unsafe website URL");
            }
            chatbot.setWebsiteUrl(updates.getWebsiteUrl());
        }

        // Update fields
        if (updates.getName() != null && !updates.getName().isBlank()) {
            chatbot.setName(xssSanitizer.sanitize(updates.getName()));
        }

        if (updates.getDescription() != null) {
            chatbot.setDescription(xssSanitizer.sanitize(updates.getDescription()));
        }

        if (updates.getPrimaryLanguage() != null) {
            chatbot.setPrimaryLanguage(updates.getPrimaryLanguage());
        }

        if (updates.getSupportedLanguages() != null) {
            chatbot.setSupportedLanguages(updates.getSupportedLanguages());
        }

        if (updates.getCustomPrompt() != null) {
            chatbot.setCustomPrompt(xssSanitizer.sanitize(updates.getCustomPrompt()));
        }

        if (updates.getBrandingConfig() != null) {
            chatbot.setBrandingConfig(xssSanitizer.sanitize(updates.getBrandingConfig()));
        }

        // Save the updated chatbot
        Chatbot savedChatbot = chatbotRepository.save(chatbot);

        // Log the update
        auditService.log(
            AuditLog.EventType.CHATBOT_UPDATED,
            AuditLog.Severity.INFO,
            "Chatbot updated: " + savedChatbot.getName(),
            user,
            null,
            null
        );

        logger.info("Updated chatbot: {}", id);
        return savedChatbot;
    }

    /**
     * Get all chatbots for a user
     *
     * @param user the user
     * @return list of chatbots owned by the user
     */
    @Transactional(readOnly = true)
    public List<Chatbot> getChatbotsForUser(User user) {
        logger.debug("Getting chatbots for user: {}", user.getId());
        return chatbotRepository.findByOwnerId(user.getId());
    }

    /**
     * Get a chatbot by ID
     *
     * @param id the chatbot ID
     * @return optional containing the chatbot if found
     */
    @Transactional(readOnly = true)
    public Optional<Chatbot> getChatbotById(Long id) {
        logger.debug("Getting chatbot by ID: {}", id);
        return chatbotRepository.findById(id);
    }

    /**
     * Toggle the active status of a chatbot
     *
     * @param id the chatbot ID
     * @param user the user requesting the toggle
     * @return the updated chatbot
     * @throws SecurityException if user is not authorized
     * @throws IllegalArgumentException if chatbot not found
     */
    @Transactional
    public Chatbot toggleChatbotStatus(Long id, User user) {
        logger.debug("Toggling status for chatbot {} by user: {}", id, user.getId());

        // Find the chatbot
        Chatbot chatbot = chatbotRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Chatbot not found: " + id));

        // Check authorization
        if (!isOwner(user, chatbot)) {
            logger.warn("User {} attempted to toggle chatbot {} status without authorization", user.getId(), id);
            throw new SecurityException("You are not authorized to modify this chatbot");
        }

        // Toggle the status
        chatbot.setActive(!chatbot.isActive());

        // Save the chatbot
        Chatbot savedChatbot = chatbotRepository.save(chatbot);

        // Log the status change
        auditService.log(
            AuditLog.EventType.CHATBOT_UPDATED,
            AuditLog.Severity.INFO,
            "Chatbot status toggled: " + savedChatbot.getName() + " -> " + (savedChatbot.isActive() ? "active" : "inactive"),
            user,
            null,
            null
        );

        logger.info("Toggled status for chatbot {}: {}", id, savedChatbot.isActive());
        return savedChatbot;
    }

    /**
     * Validate required fields
     */
    private void validateRequiredFields(Chatbot chatbot) {
        if (chatbot.getName() == null || chatbot.getName().isBlank()) {
            throw new IllegalArgumentException("Chatbot name is required");
        }

        if (chatbot.getWebsiteUrl() == null || chatbot.getWebsiteUrl().isBlank()) {
            throw new IllegalArgumentException("Website URL is required");
        }
    }

    /**
     * Sanitize chatbot fields to prevent XSS
     */
    private void sanitizeChatbotFields(Chatbot chatbot) {
        if (chatbot.getName() != null) {
            chatbot.setName(xssSanitizer.sanitize(chatbot.getName()));
        }

        if (chatbot.getDescription() != null) {
            chatbot.setDescription(xssSanitizer.sanitize(chatbot.getDescription()));
        }

        if (chatbot.getCustomPrompt() != null) {
            chatbot.setCustomPrompt(xssSanitizer.sanitize(chatbot.getCustomPrompt()));
        }

        if (chatbot.getBrandingConfig() != null) {
            chatbot.setBrandingConfig(xssSanitizer.sanitize(chatbot.getBrandingConfig()));
        }
    }

    /**
     * Check if user owns the chatbot
     */
    private boolean isOwner(User user, Chatbot chatbot) {
        return chatbot.getOwner() != null &&
               chatbot.getOwner().getId() != null &&
               chatbot.getOwner().getId().equals(user.getId());
    }
}
