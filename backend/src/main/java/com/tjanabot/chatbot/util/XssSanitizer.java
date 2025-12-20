package com.tjanabot.chatbot.util;

import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Utility class for sanitizing user input to prevent XSS attacks
 * Uses JSoup for safe HTML parsing and Apache Commons Text for HTML escaping
 * 
 * Security: ReDoS-safe implementation using JSoup instead of vulnerable regex patterns
 */
@Component
public class XssSanitizer {

    // Maximum iterations to prevent infinite loops (ReDoS protection)
    private static final int MAX_ITERATIONS = 10;
    
    // Safe regex patterns (no backtracking issues)
    // Using [^>]* instead of .*? to avoid catastrophic backtracking
    private static final Pattern SCRIPT_TAG_PATTERN = Pattern.compile("</?script[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern IFRAME_TAG_PATTERN = Pattern.compile("</?iframe[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern OBJECT_TAG_PATTERN = Pattern.compile("</?object[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMBED_PATTERN = Pattern.compile("<embed[^>]*>", Pattern.CASE_INSENSITIVE);
    // Event handlers: match "on" followed by word characters and "=" (bounded pattern)
    private static final Pattern ONCLICK_PATTERN = Pattern.compile("on[A-Za-z0-9]{1,20}\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVASCRIPT_PATTERN = Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE);
    private static final Pattern VBSCRIPT_PATTERN = Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE);

    /**
     * Sanitize a string by removing dangerous HTML/JavaScript content
     * Uses JSoup for safe HTML parsing to prevent ReDoS attacks
     *
     * @param input the input string to sanitize
     * @return sanitized string with dangerous content removed
     */
    public String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Use JSoup to safely parse and clean HTML (prevents ReDoS)
        // Safelist.none() removes all HTML tags, keeping only text
        String sanitized = Jsoup.clean(input, Safelist.none());
        
        // Additional cleanup for dangerous patterns using safe regex (bounded patterns)
        // Limit iterations to prevent DoS
        int iterations = 0;
        String previous;
        do {
            previous = sanitized;
            
            // Remove dangerous tags (safe regex - no backtracking)
            sanitized = SCRIPT_TAG_PATTERN.matcher(sanitized).replaceAll("");
            sanitized = IFRAME_TAG_PATTERN.matcher(sanitized).replaceAll("");
            sanitized = OBJECT_TAG_PATTERN.matcher(sanitized).replaceAll("");
            sanitized = EMBED_PATTERN.matcher(sanitized).replaceAll("");
            
            // Remove event handlers and dangerous protocols
            sanitized = ONCLICK_PATTERN.matcher(sanitized).replaceAll("");
            sanitized = JAVASCRIPT_PATTERN.matcher(sanitized).replaceAll("");
            sanitized = VBSCRIPT_PATTERN.matcher(sanitized).replaceAll("");
            
            iterations++;
        } while (!sanitized.equals(previous) && iterations < MAX_ITERATIONS);

        return sanitized;
    }

    /**
     * Sanitize and also HTML-escape the string
     * This is more aggressive and suitable for untrusted content
     *
     * @param input the input string to sanitize and escape
     * @return sanitized and HTML-escaped string
     */
    public String sanitizeAndEscape(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String sanitized = sanitize(input);
        return StringEscapeUtils.escapeHtml4(sanitized);
    }

    /**
     * Check if a string contains potentially dangerous content
     * Uses safe regex patterns to prevent ReDoS
     *
     * @param input the input string to check
     * @return true if dangerous content is detected
     */
    public boolean containsDangerousContent(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        // Use safe regex patterns (no backtracking issues)
        return SCRIPT_TAG_PATTERN.matcher(input).find() ||
               IFRAME_TAG_PATTERN.matcher(input).find() ||
               OBJECT_TAG_PATTERN.matcher(input).find() ||
               EMBED_PATTERN.matcher(input).find() ||
               ONCLICK_PATTERN.matcher(input).find() ||
               JAVASCRIPT_PATTERN.matcher(input).find() ||
               VBSCRIPT_PATTERN.matcher(input).find();
    }
}
