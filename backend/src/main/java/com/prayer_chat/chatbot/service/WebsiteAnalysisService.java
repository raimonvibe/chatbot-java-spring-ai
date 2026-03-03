package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.WebsiteContent;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.repository.WebsiteContentRepository;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jsoup.parser.Parser;

/**
 * Service for analyzing and crawling websites to extract content for chatbot training
 */
@Service
public class WebsiteAnalysisService {
    
    private static final Logger logger = LoggerFactory.getLogger(WebsiteAnalysisService.class);

    private final WebsiteContentRepository websiteContentRepository;
    private final UrlValidationService urlValidationService;
    private final ChatbotRepository chatbotRepository;
    private final RobotsTxtService robotsTxtService;
    private final ExecutorService executorService;
    
    @Value("${app.website-analysis.max-pages:50}")
    private int maxPages;
    
    @Value("${app.website-analysis.max-depth:3}")
    private int maxDepth;
    
    @Value("${app.website-analysis.timeout-seconds:30}")
    private int timeoutSeconds;
    
    @Value("${app.website-analysis.user-agent:AI-Chatbot-Crawler/1.0}")
    private String userAgent;

    @Value("${app.website-analysis.max-headless-pages-per-scan:5}")
    private int maxHeadlessPagesPerScan;

    private final HeadlessFetchService headlessFetchService;

    // Patterns for content filtering
    private static final Pattern SKIP_PATTERNS = Pattern.compile(
        ".*\\.(css|js|png|jpg|jpeg|gif|svg|ico|pdf|zip|mp3|mp4|avi|mov)$", 
        Pattern.CASE_INSENSITIVE
    );
    
    /** Selectors to remove before extracting main content (noise, not content). */
    private static final Set<String> SKIP_SELECTORS = Set.of(
        "nav", "header", "footer", "aside", "script", "style", "noscript", "iframe",
        ".navigation", ".menu", ".sidebar", ".ads", ".advertisement", ".cookie-banner",
        "[role=navigation]", "[role=banner]", ".social-share", ".share-buttons", ".comments"
    );

    /** Host suffixes that typically serve SPAs (client-rendered); we try headless first for these on the homepage. */
    private static final String[] SPA_HOST_SUFFIXES = { ".vercel.app", ".netlify.app", ".web.app", ".firebaseapp.com" };

    /** Selectors for main content, in priority order. First match with enough text wins. */
    private static final String[] MAIN_CONTENT_SELECTORS = {
        "main",
        "article",
        "[role=main]",
        "#__next", /* Next.js app root */
        "[data-react-root]", /* React root */
        ".post-content", ".entry-content", ".article-body", ".article-content",
        ".prose", ".page-content", ".post-body", ".content-area",
        ".main-content", "#content", "#main", ".content",
        "section"
    };
    
    public WebsiteAnalysisService(WebsiteContentRepository websiteContentRepository,
                                  UrlValidationService urlValidationService,
                                  ChatbotRepository chatbotRepository,
                                  RobotsTxtService robotsTxtService,
                                  @Autowired(required = false) HeadlessFetchService headlessFetchService) {
        this.websiteContentRepository = websiteContentRepository;
        this.urlValidationService = urlValidationService;
        this.chatbotRepository = chatbotRepository;
        this.robotsTxtService = robotsTxtService;
        this.headlessFetchService = headlessFetchService;
        // Small pool to avoid OOM on Render: crawl + Chromium + post-analysis indexing all share memory
        this.executorService = Executors.newFixedThreadPool(3);
    }

    /**
     * Normalize URL for deduplication: strip fragment and common tracking params.
     */
    private String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) return url;
        try {
            URI uri = URI.create(url);
            String path = uri.getRawPath() != null ? uri.getRawPath() : "/";
            String query = uri.getQuery();
            if (query != null && !query.isEmpty()) {
                String filtered = Arrays.stream(query.split("&"))
                    .filter(p -> !p.startsWith("utm_") && !p.equals("fbclid") && !p.startsWith("ref="))
                    .collect(Collectors.joining("&"));
                if (!filtered.isEmpty()) path = path + "?" + filtered;
            }
            return uri.getScheme() + "://" + uri.getHost() + (path.isEmpty() ? "/" : path);
        } catch (Exception e) {
            return url;
        }
    }
    
    /**
     * Analyze a website and extract content for chatbot training.
     * Uses sitemap when available to discover more pages; then crawls from homepage and sitemap URLs.
     * SECURITY: URL validated for SSRF before any network call. Uses minimal chatbot reference (id only)
     * when saving so persistence works correctly from async threads.
     */
    public CompletableFuture<List<WebsiteContent>> analyzeWebsite(Chatbot chatbot) {
        Long chatbotId = chatbot != null ? chatbot.getId() : null;
        String baseUrl = chatbot != null ? chatbot.getWebsiteUrl() : null;
        if (chatbotId == null || baseUrl == null || baseUrl.isBlank()) {
            logger.warn("Website analysis skipped: missing chatbot id or website URL");
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return CompletableFuture.supplyAsync(() -> {
            // Complete and normalize URL (add https if missing, strip fragment) so any URL is analyzable
            String urlToAnalyze = urlValidationService.completeAndValidate(baseUrl).orElse(null);
            if (urlToAnalyze == null) {
                logger.warn("Website analysis skipped: URL could not be completed or failed validation: {}", urlValidationService.extractDomain(baseUrl));
                return Collections.emptyList();
            }
            logger.info("Starting website analysis for: {} (chatbot id: {})", urlToAnalyze, chatbotId);

            // Minimal reference for persistence from async thread (avoids detached-entity issues)
            Chatbot ref = new Chatbot();
            ref.setId(chatbotId);

            // Clear previous scan so re-analyze replaces content instead of appending duplicates
            try {
                websiteContentRepository.deleteByChatbot(ref);
            } catch (Exception e) {
                logger.warn("Could not clear previous website content for chatbot {}: {}", chatbotId, e.getMessage());
            }

            Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
            List<WebsiteContent> extractedContent = Collections.synchronizedList(new ArrayList<>());

            try {
                List<String> seedUrls = collectSeedUrls(urlToAnalyze);
                logger.info("Crawl seeds: {} (homepage + {} from sitemap)", seedUrls.size(), Math.max(0, seedUrls.size() - 1));

                AtomicInteger headlessUsed = new AtomicInteger(0);
                for (String seed : seedUrls) {
                    if (visitedUrls.size() >= maxPages) break;
                    String normalized = normalizeUrl(seed);
                    if (!visitedUrls.contains(normalized) && urlValidationService.isValidAndSafe(seed)
                            && robotsTxtService.isCrawlAllowed(seed)) {
                        crawlWebsite(seed, normalized, urlToAnalyze, ref, visitedUrls, extractedContent, 0, headlessUsed);
                    } else if (urlValidationService.isValidAndSafe(seed) && !robotsTxtService.isCrawlAllowed(seed)) {
                        logger.debug("Skipping seed (disallowed by robots.txt): {}", seed);
                    }
                }
                logger.info("Website analysis completed. Extracted {} pages", extractedContent.size());
                // Persist completed/normalized URL on chatbot so future use has canonical form
                if (!urlToAnalyze.equals(baseUrl)) {
                    try {
                        chatbotRepository.findById(chatbotId).ifPresent(c -> {
                            c.setWebsiteUrl(urlToAnalyze);
                            chatbotRepository.save(c);
                            logger.debug("Updated chatbot {} websiteUrl to completed form", chatbotId);
                        });
                    } catch (Exception e) {
                        logger.warn("Could not persist completed URL for chatbot {}: {}", chatbotId, e.getMessage());
                    }
                }
            } catch (Exception e) {
                logger.error("Error during website analysis", e);
            }

            return extractedContent;
        }, executorService);
    }

    /**
     * Collect seed URLs: homepage plus same-domain URLs from sitemap (if present).
     */
    private List<String> collectSeedUrls(String websiteUrl) {
        List<String> seeds = new ArrayList<>();
        seeds.add(websiteUrl);
        try {
            URL url = new URL(websiteUrl);
            String base = url.getProtocol() + "://" + url.getHost();
            String sitemapUrl = base + "/sitemap.xml";
            List<String> sitemapUrls = fetchUrlsFromSitemap(sitemapUrl, base);
            for (String u : sitemapUrls) {
                if (seeds.size() >= maxPages) break;
                if (!seeds.contains(u) && urlValidationService.isValidAndSafe(u) && robotsTxtService.isCrawlAllowed(u)) {
                    seeds.add(u);
                }
            }
        } catch (Exception e) {
            logger.debug("No sitemap or error reading sitemap: {}", e.getMessage());
        }
        return seeds;
    }

    /**
     * Fetch URLs from sitemap (handles sitemap index and plain sitemap).
     */
    private List<String> fetchUrlsFromSitemap(String sitemapUrl, String baseUrl) {
        List<String> urls = new ArrayList<>();
        if (!urlValidationService.isValidAndSafe(sitemapUrl)) {
            logger.debug("Sitemap URL failed validation (SSRF protection): {}", sitemapUrl);
            return urls;
        }
        try {
            Document doc = Jsoup.connect(sitemapUrl)
                .userAgent(userAgent)
                .timeout(timeoutSeconds * 1000)
                .ignoreContentType(true)
                .maxBodySize(5 * 1024 * 1024)
                .get();
            Elements locs = doc.select("loc");
            if (locs.isEmpty()) {
                locs = doc.select("url loc");
            }
            for (Element loc : locs) {
                String href = loc.text();
                if (href != null && href.startsWith(baseUrl) && !SKIP_PATTERNS.matcher(href).matches()) {
                    urls.add(href);
                }
            }
            if (urls.size() > maxPages) {
                urls = urls.subList(0, maxPages);
            }
        } catch (IOException e) {
            logger.debug("Could not fetch sitemap {}: {}", sitemapUrl, e.getMessage());
        }
        return urls;
    }
    
    /**
     * Recursively crawl website pages. Uses normalizedUrl for deduplication.
     * When Jsoup returns minimal content (SPA), optionally retries with headless browser.
     *
     * @param baseUrlForDomain base website URL for same-domain link validation (not used for fetch)
     * @param chatbotRef minimal chatbot reference (id only) for persisting WebsiteContent from async thread
     * @param headlessUsed counter of headless fetches used in this scan (capped by maxHeadlessPagesPerScan)
     */
    private void crawlWebsite(String urlToFetch, String normalizedUrl, String baseUrlForDomain, Chatbot chatbotRef,
                              Set<String> visitedUrls, List<WebsiteContent> extractedContent, int depth,
                              AtomicInteger headlessUsed) {

        if (depth > maxDepth || visitedUrls.size() >= maxPages || visitedUrls.contains(normalizedUrl)) {
            return;
        }
        if (!urlValidationService.isValidAndSafe(urlToFetch)) {
            logger.warn("Blocked unsafe URL during crawl: {}", urlValidationService.extractDomain(urlToFetch));
            return;
        }
        if (!robotsTxtService.isCrawlAllowed(urlToFetch)) {
            logger.debug("Skipping URL disallowed by robots.txt: {}", urlToFetch);
            return;
        }

        visitedUrls.add(normalizedUrl);

        try {
            String finalUrl = urlToFetch;
            Document document = null;
            WebsiteContent content = null;

            // For homepage on known SPA hosts, try headless first so we get full client-rendered content
            boolean tryHeadlessFirst = depth == 0 && headlessUsed.get() < maxHeadlessPagesPerScan
                && headlessFetchService != null && headlessFetchService.isHeadlessEnabled()
                && isLikelySpaHost(urlToFetch);

            if (tryHeadlessFirst) {
                Optional<String> renderedHtml = headlessFetchService.fetchRenderedHtml(urlToFetch);
                if (renderedHtml.isPresent()) {
                    try {
                        document = Jsoup.parse(renderedHtml.get(), urlToFetch, Parser.htmlParser());
                        finalUrl = urlToFetch;
                        content = extractPageContent(chatbotRef, finalUrl, document);
                        if (content != null && content.getContent() != null && content.getContent().length() >= 50) {
                            headlessUsed.incrementAndGet();
                            logger.info("Headless-first got content for SPA homepage {} ({} chars)", urlToFetch, content.getContent().length());
                        } else {
                            content = null;
                            document = null;
                        }
                    } catch (Exception e) {
                        logger.debug("Headless-first parse failed for {}: {}", urlToFetch, e.getMessage());
                    }
                }
            }

            if (document == null) {
                Connection conn = Jsoup.connect(urlToFetch)
                    .userAgent(userAgent)
                    .timeout(timeoutSeconds * 1000)
                    .followRedirects(true);
                Connection.Response response = conn.execute();
                finalUrl = response.url().toString();
                if (!urlValidationService.isValidAndSafe(finalUrl)) {
                    logger.warn("Blocked crawl after redirect to unsafe URL: {}", urlValidationService.extractDomain(finalUrl));
                    return;
                }
                document = response.parse();
                content = extractPageContent(chatbotRef, finalUrl, document);
            }

            // If Jsoup got minimal content (likely SPA) and we have headless budget, retry with headless browser
            boolean minimalFromJsoup = content == null || (content.getContent() != null && content.getContent().length() < 200);
            if (minimalFromJsoup && headlessUsed.get() < maxHeadlessPagesPerScan
                && headlessFetchService != null && headlessFetchService.isHeadlessEnabled() && !tryHeadlessFirst) {
                Optional<String> renderedHtml = headlessFetchService.fetchRenderedHtml(urlToFetch);
                if (renderedHtml.isPresent()) {
                    try {
                        Document renderedDoc = Jsoup.parse(renderedHtml.get(), finalUrl, Parser.htmlParser());
                        WebsiteContent headlessContent = extractPageContent(chatbotRef, finalUrl, renderedDoc);
                        if (headlessContent != null && (content == null || headlessContent.getContent().length() > (content.getContent() != null ? content.getContent().length() : 0))) {
                            content = headlessContent;
                            headlessUsed.incrementAndGet();
                            logger.info("Headless fetch improved content for {} ({} chars)", urlToFetch, content.getContent().length());
                        }
                    } catch (Exception e) {
                        logger.debug("Headless HTML parse failed for {}: {}", urlToFetch, e.getMessage());
                    }
                }
            }

            boolean acceptContent = content != null && (isValidContent(content)
                || (depth == 0 && isMinimalUsableContent(content))); // First page: accept SPA fallback so chatbot has something
            if (acceptContent) {
                extractedContent.add(content);
                websiteContentRepository.save(content);
                logger.debug("Extracted content from: {}", urlToFetch);
            }

            if (depth < maxDepth && visitedUrls.size() < maxPages) {
                Elements links = document.select("a[href]");
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (Element link : links) {
                    String href = link.attr("abs:href");
                    if (href == null || href.isEmpty()) continue;
                    String norm = normalizeUrl(href);
                    if (isValidUrl(href, baseUrlForDomain) && !visitedUrls.contains(norm)
                            && robotsTxtService.isCrawlAllowed(href)) {
                        CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                            crawlWebsite(href, norm, baseUrlForDomain, chatbotRef, visitedUrls, extractedContent, depth + 1, headlessUsed),
                            executorService
                        );
                        futures.add(future);
                    }
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        } catch (IOException e) {
            logger.warn("Failed to crawl URL: {} - {}", urlToFetch, e.getMessage());
        }
    }
    
    /**
     * Extract content from a single page
     */
    private WebsiteContent extractPageContent(Chatbot chatbot, String url, Document document) {
        try {
            // Extract title (before any clone)
            String title = document.title();
            if (title == null || title.trim().isEmpty()) {
                title = extractTitleFromContent(document);
            }
            String ogTitle = extractOgTitle(document);
            if ((title == null || title.trim().isEmpty()) && ogTitle != null && !ogTitle.trim().isEmpty()) {
                title = ogTitle.trim();
            }
            
            // Extract main content
            String content = extractMainContent(document);
            
            // Extract metadata
            String metaDescription = extractMetaDescription(document);
            String ogDescription = extractOgDescription(document);
            String metaKeywords = extractMetaKeywords(document);
            String language = extractLanguage(document);
            
            // SPA / client-rendered sites: Jsoup gets minimal HTML (no JS execution). Use title + meta as fallback so we index something.
            if (content == null || content.trim().length() < 50) {
                String fallback = buildFallbackContentFromMeta(title, metaDescription, ogDescription);
                if (fallback.length() < 5) {
                    return null; // No usable content at all (not even a short title)
                }
                logger.debug("Using title+meta fallback for SPA/minimal page: {}", url);
                content = fallback;
            }

            // Prepend title and description so RAG has full context
            StringBuilder fullContent = new StringBuilder();
            if (title != null && !title.trim().isEmpty()) {
                fullContent.append("Title: ").append(title.trim()).append(". ");
            }
            if (metaDescription != null && !metaDescription.trim().isEmpty()) {
                fullContent.append("Description: ").append(metaDescription.trim()).append(". ");
            }
            fullContent.append(content);

            WebsiteContent websiteContent = new WebsiteContent(chatbot, url, title, fullContent.toString());
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
     * Extract main content from the page using multiple selectors; picks the best candidate (most substantive text).
     */
    private String extractMainContent(Document document) {
        Document working = document.clone();
        for (String selector : SKIP_SELECTORS) {
            working.select(selector).remove();
        }

        Element body = working.body();
        if (body == null) return "";

        Element best = null;
        int bestLength = 0;
        int minUseful = 50;

        for (String selector : MAIN_CONTENT_SELECTORS) {
            Elements candidates = working.select(selector);
            for (Element el : candidates) {
                String text = el.text().replaceAll("\\s+", " ").trim();
                if (text.length() >= minUseful && text.length() > bestLength) {
                    best = el;
                    bestLength = text.length();
                }
            }
            if (best != null) break;
        }

        if (best == null) {
            best = body;
        }
        String content = best.text().replaceAll("\\s+", " ").trim();
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
     * Extract og:description for SPA/SEO fallback
     */
    private String extractOgDescription(Document document) {
        Element og = document.select("meta[property=og:description]").first();
        return og != null ? og.attr("content") : null;
    }

    /**
     * Extract og:title for SPA/SEO fallback
     */
    private String extractOgTitle(Document document) {
        Element og = document.select("meta[property=og:title]").first();
        return og != null ? og.attr("content") : null;
    }

    /**
     * Build minimal content from title + meta for client-rendered (SPA) pages that have little body text.
     */
    private String buildFallbackContentFromMeta(String title, String metaDescription, String ogDescription) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.trim().isEmpty()) {
            sb.append(title.trim());
        }
        String desc = metaDescription != null && !metaDescription.trim().isEmpty()
            ? metaDescription.trim()
            : (ogDescription != null && !ogDescription.trim().isEmpty() ? ogDescription.trim() : null);
        if (desc != null) {
            if (sb.length() > 0) sb.append(". ");
            sb.append(desc);
        }
        return sb.toString();
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
     * True if the URL host is a known SPA host (e.g. Vercel, Netlify) where Jsoup often gets minimal HTML.
     */
    private boolean isLikelySpaHost(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            String host = URI.create(url).getHost();
            if (host == null) return false;
            String lower = host.toLowerCase();
            for (String suffix : SPA_HOST_SUFFIXES) {
                if (lower.endsWith(suffix)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
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
     * Check if content is valid for training (substantial content)
     */
    private boolean isValidContent(WebsiteContent content) {
        return content.getContent() != null &&
               content.getContent().length() > 100 &&
               content.getWordCount() != null && content.getWordCount() > 20;
    }

    /**
     * Relaxed check for first page (SPA/minimal HTML): accept title+meta fallback so we index at least one page.
     * Allows chatbot to answer "about this site" instead of "content not loaded" after 2 minutes.
     * Very short title-only (e.g. "Title: Lagos Health Navigator.") is accepted so client-rendered sites get one page.
     */
    private boolean isMinimalUsableContent(WebsiteContent content) {
        if (content == null || content.getContent() == null) return false;
        int len = content.getContent().length();
        int words = content.getWordCount() != null ? content.getWordCount() : 0;
        return len >= 15 && words >= 2;
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

    /**
     * Whether website content is analyzed and indexed so the chatbot can answer questions about the site.
     * Used by frontend to keep loading screen until ready.
     */
    public Map<String, Object> getAnalysisStatus(Chatbot chatbot) {
        List<WebsiteContent> contents = websiteContentRepository.findByChatbot(chatbot);
        int indexed = (int) contents.stream().filter(c -> Boolean.TRUE.equals(c.getIsIndexed())).count();
        Map<String, Object> status = new HashMap<>();
        status.put("ready", indexed > 0);
        status.put("pagesIndexed", indexed);
        return status;
    }

    /**
     * Get analyzed website content as a concatenated string (for Bible verse suggestion)
     */
    public String getAnalyzedContent(Chatbot chatbot) {
        List<WebsiteContent> contents = websiteContentRepository.findByChatbot(chatbot);

        if (contents.isEmpty()) {
            return "";
        }

        // Concatenate first 3 pages of content (or all if less than 3) for context
        StringBuilder combinedContent = new StringBuilder();
        contents.stream()
            .limit(3)
            .forEach(content -> {
                if (content.getTitle() != null) {
                    combinedContent.append(content.getTitle()).append(" ");
                }
                if (content.getMetaDescription() != null) {
                    combinedContent.append(content.getMetaDescription()).append(" ");
                }
                if (content.getContent() != null) {
                    // Limit content to first 500 characters per page
                    String truncatedContent = content.getContent().length() > 500
                        ? content.getContent().substring(0, 500)
                        : content.getContent();
                    combinedContent.append(truncatedContent).append(" ");
                }
            });

        return combinedContent.toString();
    }
}
