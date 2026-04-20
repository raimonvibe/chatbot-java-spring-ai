package com.prayer_chat.chatbot.dto;

import com.prayer_chat.chatbot.model.BibleVerse;
import com.prayer_chat.chatbot.service.ChristianContentAnalysisService.BibleVerseMatch;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO for Christian content analysis results
 */
public class ChristianContentAnalysis {
    
    private Long chatbotId;
    private String websiteUrl;
    private List<String> themes;
    private List<VerseMatch> relevantVerses;
    private double averageSimilarity;
    private int totalVersesAnalyzed;
    private int versesAboveThreshold;

    public ChristianContentAnalysis() {
    }

    public ChristianContentAnalysis(Long chatbotId, String websiteUrl, 
                                   List<BibleVerseMatch> matches, 
                                   int totalVersesAnalyzed) {
        this.chatbotId = chatbotId;
        this.websiteUrl = websiteUrl;
        this.relevantVerses = matches.stream()
                .map(match -> new VerseMatch(match.getVerse(), match.getSimilarity()))
                .collect(Collectors.toList());
        this.totalVersesAnalyzed = totalVersesAnalyzed;
        this.versesAboveThreshold = matches.size();
        
        if (!matches.isEmpty()) {
            this.averageSimilarity = matches.stream()
                    .mapToDouble(BibleVerseMatch::getSimilarity)
                    .average()
                    .orElse(0.0);
        }
    }

    // Getters and Setters
    public Long getChatbotId() {
        return chatbotId;
    }

    public void setChatbotId(Long chatbotId) {
        this.chatbotId = chatbotId;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public List<String> getThemes() {
        return themes;
    }

    public void setThemes(List<String> themes) {
        this.themes = themes;
    }

    public List<VerseMatch> getRelevantVerses() {
        return relevantVerses;
    }

    public void setRelevantVerses(List<VerseMatch> relevantVerses) {
        this.relevantVerses = relevantVerses;
    }

    public double getAverageSimilarity() {
        return averageSimilarity;
    }

    public void setAverageSimilarity(double averageSimilarity) {
        this.averageSimilarity = averageSimilarity;
    }

    public int getTotalVersesAnalyzed() {
        return totalVersesAnalyzed;
    }

    public void setTotalVersesAnalyzed(int totalVersesAnalyzed) {
        this.totalVersesAnalyzed = totalVersesAnalyzed;
    }

    public int getVersesAboveThreshold() {
        return versesAboveThreshold;
    }

    public void setVersesAboveThreshold(int versesAboveThreshold) {
        this.versesAboveThreshold = versesAboveThreshold;
    }

    /**
     * Inner class for verse match with similarity score
     */
    public static class VerseMatch {
        private Long id;
        private String reference;
        private String book;
        private Integer chapter;
        private Integer verse;
        private String text;
        private String translation;
        private double similarity;
        private int similarityPercentage;

        public VerseMatch() {
        }

        public VerseMatch(BibleVerse verse, double similarity) {
            this.id = verse.getId();
            this.reference = verse.getReference();
            this.book = verse.getBook();
            this.chapter = verse.getChapter();
            this.verse = verse.getVerse();
            this.text = verse.getText();
            this.translation = verse.getTranslation();
            this.similarity = similarity;
            this.similarityPercentage = (int) Math.round(similarity * 100);
        }

        // Getters and Setters
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
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

        public double getSimilarity() {
            return similarity;
        }

        public void setSimilarity(double similarity) {
            this.similarity = similarity;
            this.similarityPercentage = (int) Math.round(similarity * 100);
        }

        public int getSimilarityPercentage() {
            return similarityPercentage;
        }

        public void setSimilarityPercentage(int similarityPercentage) {
            this.similarityPercentage = similarityPercentage;
        }
    }
}

