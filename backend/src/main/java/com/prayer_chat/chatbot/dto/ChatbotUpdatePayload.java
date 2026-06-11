package com.prayer_chat.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.prayer_chat.chatbot.validation.SafeUrl;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Partial update body for PUT/PATCH /api/chatbots/{id}. All fields optional: {@code null} means
 * "leave existing value unchanged" (unlike deserializing into {@link com.prayer_chat.chatbot.model.Chatbot},
 * where getters and field defaults make omission indistinguishable from false).
 *
 * <p>Validation mirrors {@link ChatbotRequest} (minus required-ness) so updates can't bypass
 * the size/format constraints enforced at creation time.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatbotUpdatePayload {

    @Size(min = 2, max = 100, message = "Chatbot name must be between 2 and 100 characters")
    @Pattern(
        regexp = "^[\\p{L}\\p{N}\\p{P}\\p{Z}\\s]*$",
        message = "Chatbot name contains invalid characters"
    )
    private String name;

    @SafeUrl
    @Size(max = 500, message = "Website URL must not exceed 500 characters")
    private String websiteUrl;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @Size(max = 10, message = "Primary language code must not exceed 10 characters")
    @Pattern(
        regexp = "^[a-z]{2}(-[A-Z]{2})?$",
        message = "Invalid primary language code format"
    )
    private String primaryLanguage;

    @Size(max = 50, message = "Too many supported languages")
    private List<@Size(max = 10, message = "Language code too long") String> supportedLanguages;

    @Size(max = 2000, message = "Custom prompt must not exceed 2000 characters")
    @Pattern(regexp = "^[^<>]*$", message = "Custom prompt contains invalid characters")
    private String customPrompt;

    private Boolean isActive;

    @SafeUrl
    @Size(max = 500, message = "Webhook URL must not exceed 500 characters")
    private String webhookUrl;

    @Size(max = 20, message = "Too many webhook events")
    private List<@Size(max = 50, message = "Webhook event name too long") String> webhookEvents;

    @Size(max = 5000, message = "Quick replies must not exceed 5000 characters")
    private String quickReplies;

    @Size(max = 1000, message = "Bible verse must not exceed 1000 characters")
    private String bibleVerse;

    private Boolean christianMessagingEnabled;
    private Boolean jesusTeachingsEnabled;

    @Size(max = 5000, message = "Branding config must not exceed 5000 characters")
    private String brandingConfig;

    @Size(max = 100, message = "Avatar ID must not exceed 100 characters")
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
