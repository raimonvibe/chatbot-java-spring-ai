package com.prayer_chat.chatbot.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * Service for estimating website size BEFORE scanning to prevent costs.
 * Uses zero-cost methods (sitemap.xml, robots.txt) when possible.
 */
@Service
public class WebsiteSizeEstimator {
    
    private static final Logger logger = LoggerFactory.getLogger(WebsiteSizeEstimator.class);
    
    private static final int TIMEOUT_MS = 2000; // 2s per step (keep fast UX; SSRF validation unchanged)
    private static final String USER_AGENT = "PrayerChatCrawler/1.0 (+https://prayer-chat.com/bot)";
    
    private final UrlValidationService urlValidationService;
    
    @Autowired
    public WebsiteSizeEstimator(UrlValidationService urlValidationService) {
        this.urlValidationService = urlValidationService;
    }
    
    /**
     * Estimate website size using multiple methods (zero-cost when possible)
     * 
     * SECURITY: Validates URL for SSRF protection before estimation
     * 
     * @param websiteUrl The website URL to estimate
     * @return Estimated number of pages, or -1 if estimation failed
     */
    public int estimateSize(String websiteUrl) {
        // SECURITY: Validate URL before any network operations (SSRF protection)
        if (!urlValidationService.isValidAndSafe(websiteUrl)) {
            logger.warn("Blocked unsafe URL in size estimation: {}", websiteUrl);
            return -1; // Return failure for unsafe URLs
        }
        
        try {
            // Method 1: Try sitemap.xml (most accurate, zero cost)
            int sitemapCount = estimateFromSitemap(websiteUrl);
            if (sitemapCount > 0) {
                logger.debug("Estimated {} pages from sitemap.xml for {}", sitemapCount, websiteUrl);
                return sitemapCount;
            }
            
            // Method 2: Try robots.txt (good estimate, zero cost)
            int robotsCount = estimateFromRobots(websiteUrl);
            if (robotsCount > 0) {
                logger.debug("Estimated {} pages from robots.txt for {}", robotsCount, websiteUrl);
                return robotsCount;
            }
            
            // Method 3: Sampling method (minimal cost, fallback)
            int sampleCount = estimateFromSampling(websiteUrl);
            if (sampleCount > 0) {
                logger.debug("Estimated {} pages from sampling for {}", sampleCount, websiteUrl);
                return sampleCount;
            }
            
            // If all methods fail, return conservative estimate
            logger.warn("Could not estimate website size for {}, using default estimate of 10", websiteUrl);
            return 10; // Conservative default
            
        } catch (Exception e) {
            logger.error("Error estimating website size for {}", websiteUrl, e);
            return 10; // Conservative default on error
        }
    }
    
    /**
     * Estimate from sitemap.xml (zero cost, most accurate)
     */
    private int estimateFromSitemap(String websiteUrl) {
        try {
            URL url = new URL(websiteUrl);
            String baseUrl = url.getProtocol() + "://" + url.getHost();
            String sitemapUrl = baseUrl + "/sitemap.xml";
            
            Document doc = Jsoup.connect(sitemapUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .ignoreContentType(true)
                .get();
            
            // Count <url> or <loc> elements in sitemap
            Elements urlElements = doc.select("url, loc");
            int count = urlElements.size();
            
            if (count > 0) {
                logger.debug("Found {} URLs in sitemap.xml", count);
                return count;
            }
            
        } catch (IOException e) {
            logger.debug("No sitemap.xml found or not accessible: {}", e.getMessage());
        } catch (Exception e) {
            logger.debug("Error reading sitemap.xml: {}", e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * Estimate from robots.txt (zero cost, good estimate)
     */
    private int estimateFromRobots(String websiteUrl) {
        try {
            URL url = new URL(websiteUrl);
            String baseUrl = url.getProtocol() + "://" + url.getHost();
            String robotsUrl = baseUrl + "/robots.txt";
            
            Document doc = Jsoup.connect(robotsUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .ignoreContentType(true)
                .get();
            
            String robotsContent = doc.text();
            
            // Count Sitemap: declarations (indicates multiple sitemaps)
            int sitemapCount = (int) robotsContent.lines()
                .filter(line -> line.toLowerCase().startsWith("sitemap:"))
                .count();
            
            // If multiple sitemaps, estimate larger site
            if (sitemapCount > 1) {
                return sitemapCount * 100; // Rough estimate: 100 pages per sitemap
            }
            
            // Check for crawl-delay (indicates large site)
            if (robotsContent.toLowerCase().contains("crawl-delay")) {
                return 200; // Conservative estimate for sites with crawl-delay
            }
            
        } catch (IOException e) {
            logger.debug("No robots.txt found or not accessible: {}", e.getMessage());
        } catch (Exception e) {
            logger.debug("Error reading robots.txt: {}", e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * Estimate from sampling homepage links (minimal cost, fallback)
     */
    private int estimateFromSampling(String websiteUrl) {
        try {
            Document doc = Jsoup.connect(websiteUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();
            
            // Extract domain from URL
            URL url = new URL(websiteUrl);
            String domain = url.getHost();
            
            // Count internal links on homepage
            Elements links = doc.select("a[href]");
            Set<String> uniqueInternalLinks = new HashSet<>();
            
            for (Element link : links) {
                String href = link.attr("abs:href");
                if (href != null && !href.isEmpty()) {
                    try {
                        URL linkUrl = new URL(href);
                        // Count links from same domain
                        if (linkUrl.getHost().equals(domain) || 
                            linkUrl.getHost().endsWith("." + domain)) {
                            uniqueInternalLinks.add(href);
                        }
                    } catch (Exception e) {
                        // Skip invalid URLs
                    }
                }
            }
            
            // Estimate: homepage links * 2 (conservative multiplier)
            int estimate = uniqueInternalLinks.size() * 2;
            
            if (estimate > 0) {
                logger.debug("Estimated {} pages from homepage sampling ({} unique internal links)", 
                    estimate, uniqueInternalLinks.size());
                return estimate;
            }
            
        } catch (IOException e) {
            logger.debug("Error sampling homepage: {}", e.getMessage());
        } catch (Exception e) {
            logger.debug("Error in sampling method: {}", e.getMessage());
        }
        
        return 0;
    }
}

