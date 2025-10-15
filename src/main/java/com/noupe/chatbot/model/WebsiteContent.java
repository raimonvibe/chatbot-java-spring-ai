package com.noupe.chatbot.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing website content that has been analyzed and indexed
 */
@Entity
@Table(name = "website_contents")
public class WebsiteContent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chatbot_id", nullable = false)
    private Chatbot chatbot;
    
    @NotBlank(message = "URL is required")
    @Column(nullable = false, length = 1000)
    private String url;
    
    @NotBlank(message = "Title is required")
    @Column(nullable = false, length = 500)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @Column(columnDefinition = "TEXT")
    private String metaDescription;
    
    @Column(length = 1000)
    private String metaKeywords;
    
    @Column(length = 100)
    private String language;
    
    @Column(nullable = false)
    private Integer contentLength;
    
    @Column(nullable = false)
    private Integer wordCount;
    
    @Column(nullable = false)
    private Boolean isIndexed = false;
    
    @Column(columnDefinition = "TEXT")
    private String vectorId;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public WebsiteContent() {}
    
    public WebsiteContent(Chatbot chatbot, String url, String title, String content) {
        this.chatbot = chatbot;
        this.url = url;
        this.title = title;
        this.content = content;
        this.contentLength = content != null ? content.length() : 0;
        this.wordCount = content != null ? content.split("\\s+").length : 0;
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
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
        this.contentLength = content != null ? content.length() : 0;
        this.wordCount = content != null ? content.split("\\s+").length : 0;
    }
    
    public String getMetaDescription() {
        return metaDescription;
    }
    
    public void setMetaDescription(String metaDescription) {
        this.metaDescription = metaDescription;
    }
    
    public String getMetaKeywords() {
        return metaKeywords;
    }
    
    public void setMetaKeywords(String metaKeywords) {
        this.metaKeywords = metaKeywords;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    public Integer getContentLength() {
        return contentLength;
    }
    
    public void setContentLength(Integer contentLength) {
        this.contentLength = contentLength;
    }
    
    public Integer getWordCount() {
        return wordCount;
    }
    
    public void setWordCount(Integer wordCount) {
        this.wordCount = wordCount;
    }
    
    public Boolean getIsIndexed() {
        return isIndexed;
    }
    
    public void setIsIndexed(Boolean isIndexed) {
        this.isIndexed = isIndexed;
    }
    
    public String getVectorId() {
        return vectorId;
    }
    
    public void setVectorId(String vectorId) {
        this.vectorId = vectorId;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
