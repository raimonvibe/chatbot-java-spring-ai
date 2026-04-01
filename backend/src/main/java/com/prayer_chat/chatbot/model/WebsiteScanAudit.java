package com.prayer_chat.chatbot.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Audit log for website scans to prevent abuse via chatbot deletion.
 * 
 * This entity tracks scans independently of chatbots to prevent users from
 * bypassing scan frequency limits by deleting and recreating chatbots.
 * 
 * SECURITY: This table is NOT cascade-deleted when a chatbot is deleted,
 * ensuring scan history is preserved for rate limiting.
 */
@Entity
@Table(name = "website_scan_audits", indexes = {
    @Index(name = "idx_user_date", columnList = "user_id,scan_date"),
    @Index(name = "idx_scan_date", columnList = "scan_date")
})
public class WebsiteScanAudit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false, length = 1000)
    private String websiteUrl;
    
    @Column(nullable = false)
    private LocalDateTime scanDate;
    
    @Column(nullable = false)
    private Integer estimatedPages;
    
    @Column(nullable = false, precision = 10, scale = 4)
    private java.math.BigDecimal estimatedCost;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Optional: reference to chatbot (nullable, for audit trail)
    // This is NOT a foreign key constraint to allow deletion of chatbot
    @Column(name = "chatbot_id")
    private Long chatbotId;
    
    // Constructors
    public WebsiteScanAudit() {}
    
    public WebsiteScanAudit(User user, String websiteUrl, Integer estimatedPages, java.math.BigDecimal estimatedCost, Long chatbotId) {
        this.user = user;
        this.websiteUrl = websiteUrl;
        this.scanDate = LocalDateTime.now();
        this.estimatedPages = estimatedPages;
        this.estimatedCost = estimatedCost;
        this.chatbotId = chatbotId;
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
    
    public String getWebsiteUrl() {
        return websiteUrl;
    }
    
    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }
    
    public LocalDateTime getScanDate() {
        return scanDate;
    }
    
    public void setScanDate(LocalDateTime scanDate) {
        this.scanDate = scanDate;
    }
    
    public Integer getEstimatedPages() {
        return estimatedPages;
    }
    
    public void setEstimatedPages(Integer estimatedPages) {
        this.estimatedPages = estimatedPages;
    }
    
    public java.math.BigDecimal getEstimatedCost() {
        return estimatedCost;
    }
    
    public void setEstimatedCost(java.math.BigDecimal estimatedCost) {
        this.estimatedCost = estimatedCost;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public Long getChatbotId() {
        return chatbotId;
    }
    
    public void setChatbotId(Long chatbotId) {
        this.chatbotId = chatbotId;
    }
}

