package com.prayer_chat.chatbot.dto;

import com.prayer_chat.chatbot.validation.SafeUrl;
import jakarta.validation.constraints.*;

/**
 * Data Transfer Object for creating/updating chatbots
 * Includes URL validation and safe input handling
 */
public class ChatbotRequest {

    @NotBlank(message = "Chatbot name is required")
    @Size(min = 2, max = 100, message = "Chatbot name must be between 2 and 100 characters")
    @Pattern(
        regexp = "^[\\p{L}\\p{N}\\p{P}\\p{Z}\\s]*$",
        message = "Chatbot name contains invalid characters"
    )
    private String name;

    @NotBlank(message = "Website URL is required")
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

    @Size(max = 500, message = "Supported languages must not exceed 500 characters")
    private String supportedLanguages;

    @Size(max = 2000, message = "Custom prompt must not exceed 2000 characters")
    @Pattern(regexp = "^[^<>]*$", message = "Custom prompt contains invalid characters")
    private String customPrompt;

    @SafeUrl
    @Size(max = 500, message = "Webhook URL must not exceed 500 characters")
    private String webhookUrl;

    @Size(max = 5000, message = "Branding config must not exceed 5000 characters")
    private String brandingConfig;

    private Boolean isActive;

    // Constructors
    public ChatbotRequest() {
    }

    // Getters and Setters
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

    public String getSupportedLanguages() {
        return supportedLanguages;
    }

    public void setSupportedLanguages(String supportedLanguages) {
        this.supportedLanguages = supportedLanguages;
    }

    public String getCustomPrompt() {
        return customPrompt;
    }

    public void setCustomPrompt(String customPrompt) {
        this.customPrompt = customPrompt;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
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

    @Override
    public String toString() {
        return "ChatbotRequest{" +
                "name='" + name + '\'' +
                ", websiteUrl='" + websiteUrl + '\'' +
                ", primaryLanguage='" + primaryLanguage + '\'' +
                ", isActive=" + isActive +
                '}';
    }
}
