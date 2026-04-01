package com.prayer_chat.chatbot.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entity representing a Bible verse with embedding for semantic search
 */
@Entity
@Table(name = "bible_verses", indexes = {
    @Index(name = "idx_book_chapter_verse", columnList = "book,chapter,verse"),
    @Index(name = "idx_reference", columnList = "reference")
})
public class BibleVerse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String book;

    @Column(nullable = false)
    private Integer chapter;

    @Column(nullable = false)
    private Integer verse;

    @Column(nullable = false, length = 100)
    private String reference; // e.g., "Matthew 1:1"

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(length = 50)
    private String translation; // e.g., "World English Bible"

    /**
     * Embedding vector for semantic similarity search
     * Stored as JSON array in database (JSONB for PostgreSQL, CLOB for H2)
     */
    @Column(columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.JSON)
    private float[] embedding;

    @Column(name = "embedding_dimensions")
    private Integer embeddingDimensions; // Usually 1024 for Cohere embed-multilingual-v3.0

    /**
     * Speaker of the verse (for filtering Jesus's direct teachings)
     * Values: "Jesus", "Apostle", "Narrator", "Other", null (not yet tagged)
     */
    @Column(length = 50)
    private String speaker;

    public BibleVerse() {
    }

    public BibleVerse(String book, Integer chapter, Integer verse, String reference, String text, String translation) {
        this.book = book;
        this.chapter = chapter;
        this.verse = verse;
        this.reference = reference;
        this.text = text;
        this.translation = translation;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBook() {
        return book;
    }

    public void setBook(String book) {
        this.book = book;
    }

    public Integer getChapter() {
        return chapter;
    }

    public void setChapter(Integer chapter) {
        this.chapter = chapter;
    }

    public Integer getVerse() {
        return verse;
    }

    public void setVerse(Integer verse) {
        this.verse = verse;
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

    public String getTranslation() {
        return translation;
    }

    public void setTranslation(String translation) {
        this.translation = translation;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
        if (embedding != null) {
            this.embeddingDimensions = embedding.length;
        }
    }

    public Integer getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public void setEmbeddingDimensions(Integer embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    public String getSpeaker() {
        return speaker;
    }

    public void setSpeaker(String speaker) {
        this.speaker = speaker;
    }

    /**
     * Check if this verse is a direct teaching from Jesus
     */
    public boolean isJesusTeaching() {
        return "Jesus".equalsIgnoreCase(speaker);
    }

    @Override
    public String toString() {
        return "BibleVerse{" +
                "id=" + id +
                ", reference='" + reference + '\'' +
                ", book='" + book + '\'' +
                ", chapter=" + chapter +
                ", verse=" + verse +
                ", hasEmbedding=" + (embedding != null) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BibleVerse that = (BibleVerse) o;
        return book.equals(that.book) &&
                chapter.equals(that.chapter) &&
                verse.equals(that.verse);
    }

    @Override
    public int hashCode() {
        return book.hashCode() * 31 + chapter * 31 + verse;
    }
}

