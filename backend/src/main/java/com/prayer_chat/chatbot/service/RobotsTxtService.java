package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.util.LogSanitizer;
import org.jsoup.Jsoup;
import org.jsoup.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches and parses robots.txt per origin and answers whether a URL path
 * is allowed for our crawler (User-Agent). Aligns crawler behaviour with
 * the privacy policy (respecting site restrictions / robots.txt).
 */
@Service
public class RobotsTxtService {

    private static final Logger logger = LoggerFactory.getLogger(RobotsTxtService.class);

    private static final int ROBOTS_TXT_TIMEOUT_MS = 5_000;
    private static final int ROBOTS_TXT_MAX_BODY_BYTES = 64 * 1024; // 64KB to limit DoS via huge robots.txt
    private static final int MAX_CACHED_ORIGINS = 500;

    private final UrlValidationService urlValidationService;

    @Value("${app.website-analysis.user-agent:AI-Chatbot-Crawler/1.0}")
    private String userAgent;

    /** Per-origin parsed rules (origin -> rules for our bot or *). */
    private final Map<String, RobotsRules> cache = new ConcurrentHashMap<>();

    public RobotsTxtService(UrlValidationService urlValidationService) {
        this.urlValidationService = urlValidationService;
    }

    /**
     * Returns true if the given URL is allowed to be crawled according to
     * the origin's robots.txt for our configured User-Agent. If robots.txt
     * is absent, unparseable, or errors, returns true (allow).
     */
    public boolean isCrawlAllowed(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url);
            String origin = uri.getScheme() + "://" + uri.getHost();
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) path = "/";
            if (!path.startsWith("/")) path = "/" + path;

            if (!urlValidationService.isValidAndSafe(url)) return false;

            evictIfNeeded();
            RobotsRules rules = cache.computeIfAbsent(origin, this::fetchAndParse);
            if (rules == null) return true; // allow on fetch/parse failure
            return rules.isAllowed(path);
        } catch (Exception e) {
            logger.debug("Robots check failed for url={}, allowing: {}", LogSanitizer.sanitizeForLogging(url), e.getMessage());
            return true;
        }
    }

    /**
     * Extract the product token from the configured User-Agent for matching
     * robots.txt "User-agent:" lines (e.g. "PrayerChatCrawler" from "PrayerChatCrawler/1.0 (...)").
     */
    String getCrawlerToken() {
        if (userAgent == null || userAgent.isBlank()) return "PrayerChatCrawler";
        int slash = userAgent.indexOf('/');
        String token = slash > 0 ? userAgent.substring(0, slash).trim() : userAgent.trim();
        return token.isEmpty() ? "PrayerChatCrawler" : token;
    }

    private RobotsRules fetchAndParse(String origin) {
        String robotsUrl = origin + "/robots.txt";
        if (!urlValidationService.isValidAndSafe(robotsUrl)) {
            logger.debug("Robots URL not safe: {}", origin);
            return null;
        }
        try {
            Connection.Response response = Jsoup.connect(robotsUrl)
                .userAgent(userAgent)
                .timeout(ROBOTS_TXT_TIMEOUT_MS)
                .maxBodySize(ROBOTS_TXT_MAX_BODY_BYTES)
                .ignoreContentType(true)
                .followRedirects(true)
                .execute();
            // SSRF: ensure redirect did not send us to internal/private host
            String finalUrl = response.url() != null ? response.url().toString() : robotsUrl;
            if (!urlValidationService.isValidAndSafe(finalUrl)) {
                logger.debug("Robots.txt redirect to unsafe URL rejected: {}", LogSanitizer.sanitizeForLogging(finalUrl));
                return null;
            }
            String body = response.body();
            if (body == null) return null;
            return parseRobotsTxt(body, getCrawlerToken());
        } catch (Exception e) {
            logger.debug("Could not fetch or parse robots.txt for {}: {}", LogSanitizer.sanitizeForLogging(origin), e.getMessage());
            return null;
        }
    }

    /**
     * Parse robots.txt content and return rules for the given crawler token
     * (e.g. "PrayerChatCrawler") or "*". Uses most specific matching group (our token over *).
     */
    static RobotsRules parseRobotsTxt(String content, String crawlerToken) {
        if (content == null || content.isBlank()) {
            return new RobotsRules(Collections.emptyList(), Collections.emptyList());
        }
        List<Group> groups = new ArrayList<>();
        String currentAgent = null;
        List<String> allow = new ArrayList<>();
        List<String> disallow = new ArrayList<>();

        for (String line : content.split("\n")) {
            line = line.trim();
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase();
            String value = line.substring(colon + 1).trim();

            if ("user-agent".equals(key)) {
                if (currentAgent != null)
                    groups.add(new Group(currentAgent, new ArrayList<>(allow), new ArrayList<>(disallow)));
                currentAgent = value;
                allow.clear();
                disallow.clear();
                continue;
            }
            if (currentAgent == null) continue;

            if ("allow".equals(key)) {
                String path = normalizePath(value);
                if (!path.isEmpty()) allow.add(path);
            } else if ("disallow".equals(key)) {
                if (!value.isEmpty()) {
                    String path = normalizePath(value);
                    disallow.add(path);
                }
            }
        }
        if (currentAgent != null)
            groups.add(new Group(currentAgent, new ArrayList<>(allow), new ArrayList<>(disallow)));

        // Pick most specific: exact/prefix match for our token, else *
        String tokenLower = crawlerToken != null ? crawlerToken.toLowerCase() : "";
        for (Group g : groups) {
            String ua = g.userAgent.trim().toLowerCase();
            if (ua.equals("*")) continue;
            if (!tokenLower.isEmpty() && ua.startsWith(tokenLower))
                return new RobotsRules(g.allow, g.disallow);
        }
        for (Group g : groups) {
            if (g.userAgent.trim().equals("*"))
                return new RobotsRules(g.allow, g.disallow);
        }
        return new RobotsRules(Collections.emptyList(), Collections.emptyList());
    }

    private static class Group {
        final String userAgent;
        final List<String> allow;
        final List<String> disallow;
        Group(String userAgent, List<String> allow, List<String> disallow) {
            this.userAgent = userAgent;
            this.allow = allow;
            this.disallow = disallow;
        }
    }

    private static String normalizePath(String value) {
        if (value == null) return "";
        String path = value.trim();
        if (!path.startsWith("/")) path = "/" + path;
        return path;
    }

    private void evictIfNeeded() {
        if (cache.size() > MAX_CACHED_ORIGINS) {
            Iterator<String> it = cache.keySet().iterator();
            int remove = MAX_CACHED_ORIGINS / 4;
            while (remove > 0 && it.hasNext()) {
                it.next();
                it.remove();
                remove--;
            }
        }
    }

    /** Parsed allow/disallow paths for one origin. */
    static final class RobotsRules {
        private final List<String> allow;
        private final List<String> disallow;

        RobotsRules(List<String> allow, List<String> disallow) {
            this.allow = allow != null ? allow : Collections.emptyList();
            this.disallow = disallow != null ? disallow : Collections.emptyList();
        }

        /**
         * True if path is allowed. Longest matching rule wins; if tie, Allow wins.
         * No match => allow.
         */
        boolean isAllowed(String path) {
            if (path == null) path = "/";
            if (!path.startsWith("/")) path = "/" + path;

            int bestAllowLen = -1;
            int bestDisallowLen = -1;

            for (String a : allow) {
                if (path.startsWith(a) && a.length() > bestAllowLen)
                    bestAllowLen = a.length();
            }
            for (String d : disallow) {
                if (path.startsWith(d) && d.length() > bestDisallowLen)
                    bestDisallowLen = d.length();
            }

            if (bestAllowLen < 0 && bestDisallowLen < 0) return true;
            if (bestAllowLen < 0) return false;
            if (bestDisallowLen < 0) return true;
            return bestAllowLen >= bestDisallowLen;
        }
    }
}
