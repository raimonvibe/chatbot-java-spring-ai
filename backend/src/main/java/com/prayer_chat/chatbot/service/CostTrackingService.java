package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.config.PlanLimits;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import com.prayer_chat.chatbot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for tracking and enforcing usage-based cost limits per user account.
 * Limits are defined per plan in {@link PlanLimits#monthlyCostCapUsd}.
 */
@Service
public class CostTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(CostTrackingService.class);

    private static final BigDecimal EMBEDDING_COST_PER_MILLION_TOKENS = new BigDecimal("0.10");
    private static final BigDecimal SCAN_COST_PER_PAGE = new BigDecimal("0.0001");
    
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    
    @Autowired
    public CostTrackingService(UserRepository userRepository, SubscriptionRepository subscriptionRepository) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
    }
    
    /**
     * Check if user is in preview mode (no active paid subscription)
     */
    public boolean isPreviewMode(User user) {
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByUserId(user.getId());
        if (subscriptionOpt.isEmpty()) {
            return true; // No subscription = preview mode
        }
        
        Subscription subscription = subscriptionOpt.get();
        // Preview mode if subscription is not active or is FREE plan
        return !subscription.isActive() || subscription.getPlan() == Subscription.SubscriptionPlan.FREE;
    }
    
    /**
     * Reset monthly cost counter if needed (when new month starts)
     */
    @Transactional
    public void resetMonthlyCostIfNeeded(User user) {
        if (user.getCostResetDate() == null || 
            user.getCostResetDate().isBefore(LocalDateTime.now().minusMonths(1))) {
            user.setCurrentMonthCost(BigDecimal.ZERO);
            user.setCostResetDate(LocalDateTime.now());
            userRepository.save(user);
            logger.debug("Reset monthly cost for user: {}", user.getId());
        }
    }
    
    /**
     * Calculate cost for website scanning (embeddings + page scanning)
     */
    public BigDecimal calculateWebsiteScanCost(int pagesScanned, int tokensEmbedded) {
        // Calculate embedding cost
        BigDecimal tokensInMillions = new BigDecimal(tokensEmbedded)
            .divide(new BigDecimal(1_000_000), 6, RoundingMode.HALF_UP);
        BigDecimal embeddingCost = tokensInMillions.multiply(EMBEDDING_COST_PER_MILLION_TOKENS);
        
        // Calculate scan cost
        BigDecimal scanCost = new BigDecimal(pagesScanned).multiply(SCAN_COST_PER_PAGE);
        
        // Total cost
        BigDecimal totalCost = embeddingCost.add(scanCost);
        
        return totalCost.setScale(4, RoundingMode.HALF_UP);
    }
    
    private Subscription.SubscriptionPlan planFor(User user) {
        Optional<Subscription> sub = subscriptionRepository.findByUserId(user.getId());
        if (sub.isEmpty() || !sub.get().isActive()) return Subscription.SubscriptionPlan.FREE;
        return sub.get().getPlan();
    }

    /**
     * Check if user can perform operation without exceeding plan cost limit.
     * Uses pessimistic locking. Throws RuntimeException if limit would be exceeded.
     */
    @Transactional
    public void checkCostLimit(User user, BigDecimal estimatedCost) {
        User lockedUser = userRepository.findByIdWithLock(user.getId())
            .orElseThrow(() -> new RuntimeException("User not found: " + user.getId()));
        resetMonthlyCostIfNeeded(lockedUser);

        BigDecimal costLimit = PlanLimits.monthlyCostCapUsd(planFor(lockedUser));
        BigDecimal currentCost = lockedUser.getCurrentMonthCost();
        BigDecimal newTotalCost = currentCost.add(estimatedCost);
        if (newTotalCost.compareTo(costLimit) > 0) {
            throw new RuntimeException(
                "Monthly cost limit reached. Your plan limit is $" + costLimit + "/month. Upgrade to continue."
            );
        }
        logger.debug("Cost check passed for user {}: current=${}, estimated=${}, limit=${}",
            lockedUser.getId(), currentCost, estimatedCost, costLimit);
    }
    
    /**
     * Track cost for website scanning; enforces plan-based monthly cost cap for all users.
     */
    @Transactional
    public void trackWebsiteScanCost(User user, int pagesScanned, int tokensEmbedded) {
        User lockedUser = userRepository.findByIdWithLock(user.getId())
            .orElseThrow(() -> new RuntimeException("User not found: " + user.getId()));
        resetMonthlyCostIfNeeded(lockedUser);

        BigDecimal cost = calculateWebsiteScanCost(pagesScanned, tokensEmbedded);
        BigDecimal costLimit = PlanLimits.monthlyCostCapUsd(planFor(lockedUser));
        BigDecimal newCost = lockedUser.getCurrentMonthCost().add(cost);
        if (newCost.compareTo(costLimit) > 0) {
            throw new RuntimeException(
                "Monthly cost limit reached. Your plan limit is $" + costLimit + "/month. Upgrade to continue."
            );
        }
        lockedUser.setCurrentMonthCost(newCost);
        userRepository.save(lockedUser);
        logger.info("Tracked website scan cost for user {}: ${} ({} pages, {} tokens)",
            lockedUser.getId(), cost, pagesScanned, tokensEmbedded);
    }
    
    /**
     * Get current monthly cost for user
     */
    public BigDecimal getCurrentMonthCost(User user) {
        resetMonthlyCostIfNeeded(user);
        return user.getCurrentMonthCost();
    }
    
    /**
     * Get monthly cost limit for user (plan-based).
     */
    public BigDecimal getMonthlyCostLimit(User user) {
        return PlanLimits.monthlyCostCapUsd(planFor(user));
    }
}

