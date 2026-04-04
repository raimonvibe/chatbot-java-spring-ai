package com.prayer_chat.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Partial update body for PUT/PATCH /api/chatbots/{id}. All fields optional: {@code null} means
 * "leave existing value unchanged" (unlike deserializing into {@link com.prayer_chat.chatbot.model.Chatbot},
 * where getters and field defaults make omission indistinguishable from false).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatbotUpdatePayload {

    private String name;
    private String websiteUrl;
    private String description;
    private String primaryLanguage;
    private List<String> supportedLanguages;
    private String customPrompt;
    private Boolean isActive;
    private String webhookUrl;
    private List<String> webhookEvents;
    private String quickReplies;
    private String bibleVerse;
    private Boolean christianMessagingEnabled;
    private Boolean jesusTeachingsEnabled;
    private String brandingConfig;
    private String avatarId;

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

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

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
        return jesusTeachingsEnabled;
    }

    public void setJesusTeachingsEnabled(Boolean jesusTeachingsEnabled) {
        this.jesusTeachingsEnabled = jesusTeachingsEnabled;
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
}
