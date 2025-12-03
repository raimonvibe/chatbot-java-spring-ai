package com.tjanabot.chatbot.util;

import org.apache.commons.text.StringEscapeUtils;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Utility class for sanitizing user input to prevent XSS attacks
 * Uses Apache Commons Text for HTML escaping and custom patterns for script removal
 */
@Component
public class XssSanitizer {

    // Patterns for detecting and removing dangerous content
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern IFRAME_PATTERN = Pattern.compile("<iframe[^>]*>.*?</iframe>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern OBJECT_PATTERN = Pattern.compile("<object[^>]*>.*?</object>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern EMBED_PATTERN = Pattern.compile("<embed[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ONCLICK_PATTERN = Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVASCRIPT_PATTERN = Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE);
    private static final Pattern VBSCRIPT_PATTERN = Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE);

    /**
     * Sanitize a string by removing dangerous HTML/JavaScript content
     *
     * @param input the input string to sanitize
     * @return sanitized string with dangerous content removed
     */
    public String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String sanitized = input;

        // Remove dangerous HTML tags
        sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = IFRAME_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = OBJECT_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = EMBED_PATTERN.matcher(sanitized).replaceAll("");

        // Remove event handlers and dangerous protocols
        sanitized = ONCLICK_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = JAVASCRIPT_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = VBSCRIPT_PATTERN.matcher(sanitized).replaceAll("");

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
     *
     * @param input the input string to check
     * @return true if dangerous content is detected
     */
    public boolean containsDangerousContent(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        return SCRIPT_PATTERN.matcher(input).find() ||
               IFRAME_PATTERN.matcher(input).find() ||
               OBJECT_PATTERN.matcher(input).find() ||
               EMBED_PATTERN.matcher(input).find() ||
               ONCLICK_PATTERN.matcher(input).find() ||
               JAVASCRIPT_PATTERN.matcher(input).find() ||
               VBSCRIPT_PATTERN.matcher(input).find();
    }
}
