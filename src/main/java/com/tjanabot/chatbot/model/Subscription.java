package com.tjanabot.chatbot.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a user's subscription status
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String stripeCustomerId;

    @Column
    private String stripeSubscriptionId;

    @Column
    private String stripePriceId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status = SubscriptionStatus.INACTIVE;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SubscriptionPlan plan = SubscriptionPlan.FREE;

    @Column
    private LocalDateTime currentPeriodStart;

    @Column
    private LocalDateTime currentPeriodEnd;

    @Column
    private LocalDateTime canceledAt;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Enum for subscription status
    public enum SubscriptionStatus {
        ACTIVE,
        PAST_DUE,
        CANCELED,
        INCOMPLETE,
        INCOMPLETE_EXPIRED,
        TRIALING,
        UNPAID,
        INACTIVE
    }

    // Enum for subscription plans
    public enum SubscriptionPlan {
        FREE,
        BASIC,
        PRO,
        ENTERPRISE
    }

    // Helper methods
    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIALING;
    }

    public boolean canUseChatbot() {
        // For paid-only model, user must have an active subscription
        return isActive() && (plan == SubscriptionPlan.BASIC ||
                             plan == SubscriptionPlan.PRO ||
                             plan == SubscriptionPlan.ENTERPRISE);
    }

    // Constructors
    public Subscription() {}

    public Subscription(User user, String stripeCustomerId) {
        this.user = user;
        this.stripeCustomerId = stripeCustomerId;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public void setStripeCustomerId(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }

    public String getStripeSubscriptionId() {
        return stripeSubscriptionId;
    }

    public void setStripeSubscriptionId(String stripeSubscriptionId) {
        this.stripeSubscriptionId = stripeSubscriptionId;
    }

    public String getStripePriceId() {
        return stripePriceId;
    }

    public void setStripePriceId(String stripePriceId) {
        this.stripePriceId = stripePriceId;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

    public SubscriptionPlan getPlan() {
        return plan;
    }

    public void setPlan(SubscriptionPlan plan) {
        this.plan = plan;
    }

    public LocalDateTime getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public void setCurrentPeriodStart(LocalDateTime currentPeriodStart) {
        this.currentPeriodStart = currentPeriodStart;
    }

    public LocalDateTime getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public void setCurrentPeriodEnd(LocalDateTime currentPeriodEnd) {
        this.currentPeriodEnd = currentPeriodEnd;
    }

    public LocalDateTime getCanceledAt() {
        return canceledAt;
    }

    public void setCanceledAt(LocalDateTime canceledAt) {
        this.canceledAt = canceledAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
