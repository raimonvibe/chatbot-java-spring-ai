package com.prayer_chat.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for chat requests
 * Includes comprehensive input validation
 */
public class ChatRequest {

    @NotBlank(message = "Message is required")
    @Size(min = 1, max = 2000, message = "Message must be between 1 and 2000 characters")
    /** \p{S} allows Unicode symbols (e.g. emoji); message bodies are rendered as plain text in clients. */
    @Pattern(
        regexp = "^[\\p{L}\\p{N}\\p{P}\\p{Z}\\p{M}\\p{S}\\s]*$",
        message = "Message contains invalid characters"
    )
    private String message;

    @Size(max = 100, message = "Session ID must not exceed 100 characters")
    @Pattern(
        regexp = "^[a-zA-Z0-9_-]*$",
        message = "Session ID can only contain alphanumeric characters, hyphens, and underscores"
    )
    private String sessionId;

    @Size(max = 10, message = "Language code must not exceed 10 characters")
    @Pattern(
        regexp = "^[a-z]{2}(-[A-Z]{2})?$",
        message = "Invalid language code format (expected: en, en-US, etc.)"
    )
    private String language;

    /**
     * Optional Cloudflare Turnstile token (used by the public embed widget when enabled).
     * Kept unvalidated beyond size so Turnstile token formats can evolve.
     */
    @Size(max = 5000, message = "Turnstile token too long")
    private String turnstileToken;

    // Constructors
    public ChatRequest() {
    }

    public ChatRequest(String message, String sessionId, String language) {
        this.message = message;
        this.sessionId = sessionId;
        this.language = language;
    }

    // Getters and Setters
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTurnstileToken() {
        return turnstileToken;
    }

    public void setTurnstileToken(String turnstileToken) {
        this.turnstileToken = turnstileToken;
    }

    @Override
    public String toString() {
        return "ChatRequest{" +
                "message='" + (message != null ? message.substring(0, Math.min(50, message.length())) + "..." : null) + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", language='" + language + '\'' +
                ", turnstileToken='" + (turnstileToken != null ? "[present]" : null) + '\'' +
                '}';
    }
}
