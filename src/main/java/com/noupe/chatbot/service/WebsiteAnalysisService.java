package com.noupe.chatbot.service;

import com.noupe.chatbot.model.Chatbot;
import com.noupe.chatbot.model.WebsiteContent;
import com.noupe.chatbot.repository.WebsiteContentRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Service for analyzing and crawling websites to extract content for chatbot training
 */
@Service
public class WebsiteAnalysisService {
    
    private static final Logger logger = LoggerFactory.getLogger(WebsiteAnalysisService.class);
    
    private final WebsiteContentRepository websiteContentRepository;
    private final RestTemplate restTemplate;
    private final ExecutorService executorService;
    
    @Value("${app.website-analysis.max-pages:50}")
    private int maxPages;
    
    @Value("${app.website-analysis.max-depth:3}")
    private int maxDepth;
    
    @Value("${app.website-analysis.timeout-seconds:30}")
    private int timeoutSeconds;
    
    @Value("${app.website-analysis.user-agent:AI-Chatbot-Crawler/1.0}")
    private String userAgent;
    
    // Patterns for content filtering
    private static final Pattern SKIP_PATTERNS = Pattern.compile(
        ".*\\.(css|js|png|jpg|jpeg|gif|svg|ico|pdf|zip|mp3|mp4|avi|mov)$", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Set<String> SKIP_SELECTORS = Set.of(
        "nav", "header", "footer", "aside", "script", "style", 
        ".navigation", ".menu", ".sidebar", ".ads", ".advertisement"
    );
    
    public WebsiteAnalysisService(WebsiteContentRepository websiteContentRepository) {
        this.websiteContentRepository = websiteContentRepository;
        this.restTemplate = new RestTemplate();
        this.executorService = Executors.newFixedThreadPool(10);
    }
    
    /**
     * Analyze a website and extract content for chatbot training
     */
    public CompletableFuture<List<WebsiteContent>> analyzeWebsite(Chatbot chatbot) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Starting website analysis for: {}", chatbot.getWebsiteUrl());
            
            Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
            List<WebsiteContent> extractedContent = new ArrayList<>();
            
            try {
                crawlWebsite(chatbot.getWebsiteUrl(), chatbot, visitedUrls, extractedContent, 0);
                logger.info("Website analysis completed. Extracted {} pages", extractedContent.size());
            } catch (Exception e) {
                logger.error("Error during website analysis", e);
            }
            
            return extractedContent;
        }, executorService);
    }
    
    /**
     * Recursively crawl website pages
     */
    private void crawlWebsite(String url, Chatbot chatbot, Set<String> visitedUrls, 
                            List<WebsiteContent> extractedContent, int depth) {
        
        if (depth > maxDepth || visitedUrls.size() >= maxPages || visitedUrls.contains(url)) {
            return;
        }
        
        visitedUrls.add(url);
        
        try {
            Document document = Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(timeoutSeconds * 1000)
                .followRedirects(true)
                .get();
            
            // Extract content from current page
            WebsiteContent content = extractPageContent(chatbot, url, document);
            if (content != null && isValidContent(content)) {
                extractedContent.add(content);
                websiteContentRepository.save(content);
                logger.debug("Extracted content from: {}", url);
            }
            
            // Find and crawl linked pages
            if (depth < maxDepth) {
                Elements links = document.select("a[href]");
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                
                for (Element link : links) {
                    String href = link.attr("abs:href");
                    if (isValidUrl(href, chatbot.getWebsiteUrl())) {
                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> 
                            crawlWebsite(href, chatbot, visitedUrls, extractedContent, depth + 1)
                        );
                        futures.add(future);
                    }
                }
                
                // Wait for all parallel crawls to complete
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
            
        } catch (IOException e) {
            logger.warn("Failed to crawl URL: {} - {}", url, e.getMessage());
        }
    }
    
    /**
     * Extract content from a single page
     */
    private WebsiteContent extractPageContent(Chatbot chatbot, String url, Document document) {
        try {
            // Remove unwanted elements
            for (String selector : SKIP_SELECTORS) {
                document.select(selector).remove();
            }
            
            // Extract title
            String title = document.title();
            if (title == null || title.trim().isEmpty()) {
                title = extractTitleFromContent(document);
            }
            
            // Extract main content
            String content = extractMainContent(document);
            
            // Extract metadata
            String metaDescription = extractMetaDescription(document);
            String metaKeywords = extractMetaKeywords(document);
            String language = extractLanguage(document);
            
            if (content == null || content.trim().length() < 100) {
                return null; // Skip pages with insufficient content
            }
            
            WebsiteContent websiteContent = new WebsiteContent(chatbot, url, title, content);
            websiteContent.setMetaDescription(metaDescription);
            websiteContent.setMetaKeywords(metaKeywords);
            websiteContent.setLanguage(language);
            
            return websiteContent;
            
        } catch (Exception e) {
            logger.error("Error extracting content from: {}", url, e);
            return null;
        }
    }
    
    /**
     * Extract main content from the page
     */
    private String extractMainContent(Document document) {
        // Try to find main content area
        Element mainContent = document.select("main").first();
        if (mainContent == null) {
            mainContent = document.select("article").first();
        }
        if (mainContent == null) {
            mainContent = document.select(".content, .main-content, #content, #main").first();
        }
        if (mainContent == null) {
            mainContent = document.body();
        }
        
        // Extract text content
        String content = mainContent.text();
        
        // Clean up the content
        content = content.replaceAll("\\s+", " ").trim();
        
        return content;
    }
    
    /**
     * Extract title from content if page title is missing
     */
    private String extractTitleFromContent(Document document) {
        Element h1 = document.select("h1").first();
        if (h1 != null) {
            return h1.text();
        }
        
        Element title = document.select("title").first();
        if (title != null) {
            return title.text();
        }
        
        return "Untitled Page";
    }
    
    /**
     * Extract meta description
     */
    private String extractMetaDescription(Document document) {
        Element metaDesc = document.select("meta[name=description]").first();
        return metaDesc != null ? metaDesc.attr("content") : null;
    }
    
    /**
     * Extract meta keywords
     */
    private String extractMetaKeywords(Document document) {
        Element metaKeywords = document.select("meta[name=keywords]").first();
        return metaKeywords != null ? metaKeywords.attr("content") : null;
    }
    
    /**
     * Extract page language
     */
    private String extractLanguage(Document document) {
        Element html = document.select("html").first();
        if (html != null) {
            String lang = html.attr("lang");
            if (!lang.isEmpty()) {
                return lang.substring(0, 2); // Extract language code
            }
        }
        return "en"; // Default to English
    }
    
    /**
     * Check if URL is valid for crawling
     */
    private boolean isValidUrl(String url, String baseUrl) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        try {
            URL urlObj = new URL(url);
            URL baseUrlObj = new URL(baseUrl);
            
            // Check if it's the same domain
            if (!urlObj.getHost().equals(baseUrlObj.getHost())) {
                return false;
            }
            
            // Skip file extensions we don't want
            if (SKIP_PATTERNS.matcher(url).matches()) {
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if content is valid for training
     */
    private boolean isValidContent(WebsiteContent content) {
        return content.getContent() != null && 
               content.getContent().length() > 100 && 
               content.getWordCount() > 20;
    }
    
    /**
     * Get analysis statistics
     */
    public Map<String, Object> getAnalysisStats(Chatbot chatbot) {
        List<WebsiteContent> contents = websiteContentRepository.findByChatbot(chatbot);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPages", contents.size());
        stats.put("totalWords", contents.stream().mapToInt(WebsiteContent::getWordCount).sum());
        stats.put("totalCharacters", contents.stream().mapToInt(WebsiteContent::getContentLength).sum());
        stats.put("indexedPages", contents.stream().mapToInt(c -> c.getIsIndexed() ? 1 : 0).sum());
        
        return stats;
    }
}
