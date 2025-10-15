package com.noupe.chatbot.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a conversation between a user and a chatbot
 */
@Entity
@Table(name = "conversations")
public class Conversation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chatbot_id", nullable = false)
    private Chatbot chatbot;
    
    @NotBlank(message = "Session ID is required")
    @Column(nullable = false, length = 100)
    private String sessionId;
    
    @Column(length = 100)
    private String userLanguage;
    
    @Column(length = 45)
    private String userIp;
    
    @Column(length = 500)
    private String userAgent;
    
    @Column(nullable = false)
    private Boolean isActive = true;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column
    private LocalDateTime endedAt;
    
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Message> messages = new ArrayList<>();
    
    // Constructors
    public Conversation() {}
    
    public Conversation(Chatbot chatbot, String sessionId) {
        this.chatbot = chatbot;
        this.sessionId = sessionId;
    }
    
    // Helper methods
    public void endConversation() {
        this.isActive = false;
        this.endedAt = LocalDateTime.now();
    }
    
    public int getMessageCount() {
        return messages != null ? messages.size() : 0;
    }
    
    public long getDurationInMinutes() {
        if (endedAt != null && createdAt != null) {
            return java.time.Duration.between(createdAt, endedAt).toMinutes();
        }
        return 0;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Chatbot getChatbot() {
        return chatbot;
    }
    
    public void setChatbot(Chatbot chatbot) {
        this.chatbot = chatbot;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getUserLanguage() {
        return userLanguage;
    }
    
    public void setUserLanguage(String userLanguage) {
        this.userLanguage = userLanguage;
    }
    
    public String getUserIp() {
        return userIp;
    }
    
    public void setUserIp(String userIp) {
        this.userIp = userIp;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    public Boolean getIsActive() {
        return isActive;
    }
    
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getEndedAt() {
        return endedAt;
    }
    
    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }
    
    public List<Message> getMessages() {
        return messages;
    }
    
    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }
}
