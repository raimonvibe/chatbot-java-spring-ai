package com.prayer_chat.chatbot.dto;

import com.prayer_chat.chatbot.model.BibleVerse;

/**
 * DTO representing a Jesus teaching with relevance information
 * Used for "What Jesus Would Say" feature
 */
public class JesusTeaching {
    private BibleVerse verse;
    private String reference;      // "Matthew 5:16"
    private String text;           // Verse text
    private double similarity;     // 0.0 to 1.0 (cosine similarity score)
    private String context;        // Context of the teaching (e.g., "Sermon on the Mount")
    private String application;    // How it applies to the current context (optional, AI-generated)

    public JesusTeaching() {
    }

    public JesusTeaching(BibleVerse verse, double similarity) {
        this.verse = verse;
        this.reference = verse.getReference();
        this.text = verse.getText();
        this.similarity = similarity;
    }

    public JesusTeaching(BibleVerse verse, double similarity, String context) {
        this.verse = verse;
        this.reference = verse.getReference();
        this.text = verse.getText();
        this.similarity = similarity;
        this.context = context;
    }

    // Getters and Setters
    public BibleVerse getVerse() {
        return verse;
    }

    public void setVerse(BibleVerse verse) {
        this.verse = verse;
        if (verse != null) {
            this.reference = verse.getReference();
            this.text = verse.getText();
        }
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(double similarity) {
        this.similarity = similarity;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    @Override
    public String toString() {
        return "JesusTeaching{" +
                "reference='" + reference + '\'' +
                ", similarity=" + String.format("%.2f", similarity) +
                ", context='" + context + '\'' +
                '}';
    }
}

