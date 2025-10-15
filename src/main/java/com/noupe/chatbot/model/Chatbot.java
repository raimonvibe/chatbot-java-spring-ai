package com.noupe.chatbot.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a chatbot configuration
 */
@Entity
@Table(name = "chatbots")
public class Chatbot {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    @Column(nullable = false)
    private String name;
    
    @NotBlank(message = "Website URL is required")
    @Column(nullable = false)
    private String websiteUrl;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false)
    private String primaryLanguage = "en";
    
    @ElementCollection
    @CollectionTable(name = "chatbot_languages", joinColumns = @JoinColumn(name = "chatbot_id"))
    @Column(name = "language")
    private List<String> supportedLanguages = new ArrayList<>();
    
    @Column(columnDefinition = "TEXT")
    private String customPrompt;
    
    @Column(columnDefinition = "TEXT")
    private String brandingConfig;
    
    @Column(nullable = false)
    private Boolean isActive = true;
    
    @Column(nullable = false)
    private String embedCode;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "chatbot", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Conversation> conversations = new ArrayList<>();
    
    @OneToMany(mappedBy = "chatbot", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<WebsiteContent> websiteContents = new ArrayList<>();
    
    // Constructors
    public Chatbot() {}
    
    public Chatbot(String name, String websiteUrl) {
        this.name = name;
        this.websiteUrl = websiteUrl;
        this.embedCode = generateEmbedCode();
    }
    
    // Helper method to generate unique embed code
    private String generateEmbedCode() {
        return String.format("noupe-chatbot-%d", System.currentTimeMillis());
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getWebsiteUrl() {
        return websiteUrl;
    }
    
    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getPrimaryLanguage() {
        return primaryLanguage;
    }
    
    public void setPrimaryLanguage(String primaryLanguage) {
        this.primaryLanguage = primaryLanguage;
    }
    
    public List<String> getSupportedLanguages() {
        return supportedLanguages;
    }
    
    public void setSupportedLanguages(List<String> supportedLanguages) {
        this.supportedLanguages = supportedLanguages;
    }
    
    public String getCustomPrompt() {
        return customPrompt;
    }
    
    public void setCustomPrompt(String customPrompt) {
        this.customPrompt = customPrompt;
    }
    
    public String getBrandingConfig() {
        return brandingConfig;
    }
    
    public void setBrandingConfig(String brandingConfig) {
        this.brandingConfig = brandingConfig;
    }
    
    public Boolean getIsActive() {
        return isActive;
    }
    
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    
    public String getEmbedCode() {
        return embedCode;
    }
    
    public void setEmbedCode(String embedCode) {
        this.embedCode = embedCode;
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
    
    public List<Conversation> getConversations() {
        return conversations;
    }
    
    public void setConversations(List<Conversation> conversations) {
        this.conversations = conversations;
    }
    
    public List<WebsiteContent> getWebsiteContents() {
        return websiteContents;
    }
    
    public void setWebsiteContents(List<WebsiteContent> websiteContents) {
        this.websiteContents = websiteContents;
    }
}
