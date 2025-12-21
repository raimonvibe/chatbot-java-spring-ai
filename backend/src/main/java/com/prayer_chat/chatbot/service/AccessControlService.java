package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for access control checks (preview mode, integration script access, etc.)
 */
@Service
public class AccessControlService {
    
    private static final Logger logger = LoggerFactory.getLogger(AccessControlService.class);
    
    private final SubscriptionRepository subscriptionRepository;
    private final CostTrackingService costTrackingService;
    
    @Autowired
    public AccessControlService(SubscriptionRepository subscriptionRepository, 
                               CostTrackingService costTrackingService) {
        this.subscriptionRepository = subscriptionRepository;
        this.costTrackingService = costTrackingService;
    }
    
    /**
     * Check if user is in preview mode (no active paid subscription)
     */
    public boolean isPreviewMode(User user) {
        return costTrackingService.isPreviewMode(user);
    }
    
    /**
     * Check if user can access integration script (requires paid subscription)
     */
    public boolean canAccessIntegrationScript(User user) {
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByUserId(user.getId());
        
        if (subscriptionOpt.isEmpty()) {
            logger.debug("User {} has no subscription - cannot access integration script", user.getId());
            return false;
        }
        
        Subscription subscription = subscriptionOpt.get();
        
        // Check if subscription is active and paid
        boolean isActive = subscription.isActive();
        boolean isPaid = subscription.getPlan() != Subscription.SubscriptionPlan.FREE;
        
        boolean canAccess = isActive && isPaid;
        
        if (!canAccess) {
            logger.debug("User {} subscription status: active={}, plan={} - cannot access integration script", 
                user.getId(), isActive, subscription.getPlan());
        }
        
        return canAccess;
    }
    
    /**
     * Check if user can create more chatbots
     * Preview mode: 1 chatbot max
     * Paid mode: unlimited (or based on subscription plan)
     */
    public boolean canCreateChatbot(User user, long currentChatbotCount) {
        if (isPreviewMode(user)) {
            // Preview mode: max 1 chatbot
            return currentChatbotCount < 1;
        } else {
            // Paid mode: unlimited (or could be based on subscription plan)
            return true;
        }
    }
    
    /**
     * Get maximum chatbots allowed for user
     */
    public int getMaxChatbotsAllowed(User user) {
        if (isPreviewMode(user)) {
            return 1; // Preview mode: 1 chatbot
        } else {
            // Paid mode: unlimited (or based on subscription plan)
            Optional<Subscription> subscriptionOpt = subscriptionRepository.findByUserId(user.getId());
            if (subscriptionOpt.isPresent()) {
                // Could be based on subscription plan in the future
                // For now, paid users get unlimited
                return Integer.MAX_VALUE;
            }
            return 1; // Default to 1 if no subscription found
        }
    }
    
    /**
     * Check if user has active subscription (for general access)
     */
    public boolean hasActiveSubscription(User user) {
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByUserId(user.getId());
        if (subscriptionOpt.isEmpty()) {
            return false;
        }
        Subscription subscription = subscriptionOpt.get();
        return subscription.canUseChatbot();
    }
}

