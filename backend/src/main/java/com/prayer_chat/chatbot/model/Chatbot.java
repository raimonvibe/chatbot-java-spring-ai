package com.prayer_chat.chatbot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
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

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = true)
    private User owner;

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
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "chatbot_languages", joinColumns = @JoinColumn(name = "chatbot_id"))
    @Column(name = "language")
    private List<String> supportedLanguages = new ArrayList<>();
    
    @Column(columnDefinition = "TEXT")
    private String customPrompt;
    
    @Column(columnDefinition = "TEXT")
    private String brandingConfig;

    /** Optional avatar id: "1".."6" for static images, or null/blank for no avatar. Validated on update. */
    @Column(name = "avatar_id", length = 2)
    private String avatarId;

    // NEW FEATURE: Webhook Integration
    @Column(length = 500)
    private String webhookUrl;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "chatbot_webhook_events", joinColumns = @JoinColumn(name = "chatbot_id"))
    @Column(name = "event")
    private List<String> webhookEvents = new ArrayList<>();

    // NEW FEATURE: Quick Replies
    @Column(columnDefinition = "TEXT")
    private String quickReplies; // JSON array of quick reply configurations

    // Christian Messaging Features
    @Column(columnDefinition = "TEXT")
    private String bibleVerse; // Bible verse relevant to website topic

    @Column(nullable = false)
    private Boolean christianMessagingEnabled = true; // Enable Christian values in responses

    /**
     * Enable "What Jesus Would Say" feature
     * When enabled, chatbot responses will be inspired by Jesus's direct teachings
     * from the Gospels (Matthew, Mark, Luke, John)
     * 
     * Note: Column is nullable to allow migration of existing NULL values.
     * Migration runner will set NULL values to false on startup.
     */
    @Column(name = "jesus_teachings_enabled", nullable = true)
    private Boolean jesusTeachingsEnabled = false;

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
    @JsonIgnore
    @OneToMany(mappedBy = "chatbot", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Conversation> conversations = new ArrayList<>();

    @JsonIgnore
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
        return String.format("prayer-chat-bot-%d", System.currentTimeMillis());
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
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

    public String getAvatarId() {
        return avatarId;
    }

    public void setAvatarId(String avatarId) {
        this.avatarId = avatarId;
    }
    
    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    // Convenience methods for active status
    public boolean isActive() {
        return Boolean.TRUE.equals(isActive);
    }

    public void setActive(boolean active) {
        this.isActive = active;
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

    // Getters and Setters for new features
    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public List<String> getWebhookEvents() {
        return webhookEvents;
    }

    public void setWebhookEvents(List<String> webhookEvents) {
        this.webhookEvents = webhookEvents;
    }

    public String getQuickReplies() {
        return quickReplies;
    }

    public void setQuickReplies(String quickReplies) {
        this.quickReplies = quickReplies;
    }

    public String getBibleVerse() {
        return bibleVerse;
    }

    public void setBibleVerse(String bibleVerse) {
        this.bibleVerse = bibleVerse;
    }

    public Boolean getChristianMessagingEnabled() {
        return christianMessagingEnabled;
    }

    public void setChristianMessagingEnabled(Boolean christianMessagingEnabled) {
        this.christianMessagingEnabled = christianMessagingEnabled;
    }

    public Boolean getJesusTeachingsEnabled() {
        // Return false if null (for backward compatibility with existing rows)
        return jesusTeachingsEnabled != null ? jesusTeachingsEnabled : false;
    }

    public void setJesusTeachingsEnabled(Boolean jesusTeachingsEnabled) {
        this.jesusTeachingsEnabled = jesusTeachingsEnabled;
    }
}
