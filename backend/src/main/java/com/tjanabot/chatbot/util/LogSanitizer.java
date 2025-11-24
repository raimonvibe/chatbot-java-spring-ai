package com.tjanabot.chatbot.util;

import java.util.regex.Pattern;

/**
 * Utility class for sanitizing sensitive data in logs
 * Prevents accidental exposure of API keys, passwords, tokens, etc.
 */
public class LogSanitizer {

    // Patterns for sensitive data
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
        "(api[_-]?key[\"']?\\s*[:=]\\s*[\"']?)([\\w-]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "(password[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        "(token[\"']?\\s*[:=]\\s*[\"']?)([\\w-]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
        "(Bearer\\s+)([\\w\\.-]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SECRET_PATTERN = Pattern.compile(
        "(secret[\"']?\\s*[:=]\\s*[\"']?)([\\w-]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile(
        "(Authorization[\"']?\\s*[:=]\\s*[\"']?)(?!\\s*Bearer)([^\n\"']+)",
        Pattern.CASE_INSENSITIVE
    );

    // Database URL pattern (matches jdbc:...:password@... or protocol://user:password@...)
    private static final Pattern DATABASE_URL_PATTERN = Pattern.compile(
        "://([^:/@]+):([^@/]+)@",
        Pattern.CASE_INSENSITIVE
    );

    // Email pattern (partially redact)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})"
    );

    // IP Address pattern (partially redact)
    private static final Pattern IP_PATTERN = Pattern.compile(
        "\\b(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\b"
    );

    // Placeholder text for redacted content
    private static final String REDACTED = "***REDACTED***";

    /**
     * Sanitize a log message by removing sensitive data
     *
     * @param message The message to sanitize
     * @return Sanitized message with sensitive data redacted
     */
    public static String sanitize(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String sanitized = message;

        // Redact API keys
        sanitized = API_KEY_PATTERN.matcher(sanitized).replaceAll("$1" + REDACTED);

        // Redact passwords
        sanitized = PASSWORD_PATTERN.matcher(sanitized).replaceAll("$1" + REDACTED);

        // Redact tokens
        sanitized = TOKEN_PATTERN.matcher(sanitized).replaceAll("$1" + REDACTED);

        // Redact Bearer tokens
        sanitized = BEARER_TOKEN_PATTERN.matcher(sanitized).replaceAll("$1" + REDACTED);

        // Redact secrets
        sanitized = SECRET_PATTERN.matcher(sanitized).replaceAll("$1" + REDACTED);

        // Redact Authorization headers
        sanitized = AUTHORIZATION_PATTERN.matcher(sanitized).replaceAll("$1" + REDACTED);

        // Redact database URL passwords (protocol://user:password@host)
        sanitized = DATABASE_URL_PATTERN.matcher(sanitized).replaceAll("://$1:" + REDACTED + "@");

        // Partially redact emails (keep first 2 chars and domain)
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll(matchResult -> {
            String localPart = matchResult.group(1);
            String domain = matchResult.group(2);
            String redactedLocal = localPart.length() > 2 ?
                    localPart.substring(0, 2) + "***" :
                    "***";
            return redactedLocal + "@" + domain;
        });

        // Partially redact IP addresses (keep first octet)
        sanitized = IP_PATTERN.matcher(sanitized).replaceAll("$1.*.*.*");

        return sanitized;
    }

    /**
     * Sanitize an exception message and stack trace
     *
     * @param throwable The exception to sanitize
     * @return Sanitized exception message
     */
    public static String sanitizeException(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(sanitize(throwable.getMessage()));

        // Include cause if present
        Throwable cause = throwable.getCause();
        if (cause != null && cause != throwable) {
            sb.append(" | Caused by: ").append(sanitize(cause.getMessage()));
        }

        return sb.toString();
    }

    /**
     * Check if a string likely contains sensitive data
     *
     * @param message The message to check
     * @return true if sensitive data is detected
     */
    public static boolean containsSensitiveData(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }

        return API_KEY_PATTERN.matcher(message).find() ||
               PASSWORD_PATTERN.matcher(message).find() ||
               TOKEN_PATTERN.matcher(message).find() ||
               BEARER_TOKEN_PATTERN.matcher(message).find() ||
               SECRET_PATTERN.matcher(message).find() ||
               AUTHORIZATION_PATTERN.matcher(message).find();
    }

    /**
     * Sanitize a URL by removing query parameters that might contain sensitive data
     *
     * @param url The URL to sanitize
     * @return Sanitized URL with query parameters removed
     */
    public static String sanitizeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        // Remove query parameters
        int queryIndex = url.indexOf('?');
        if (queryIndex > 0) {
            return url.substring(0, queryIndex) + "?***";
        }

        return url;
    }

    /**
     * Truncate a string for logging (prevents log injection and huge logs)
     *
     * @param message The message to truncate
     * @param maxLength Maximum length
     * @return Truncated message
     */
    public static String truncate(String message, int maxLength) {
        if (message == null) {
            return null;
        }

        if (message.length() <= maxLength) {
            return message;
        }

        return message.substring(0, maxLength) + "... (truncated)";
    }

    /**
     * Remove newlines and carriage returns to prevent log injection
     *
     * @param message The message to clean
     * @return Cleaned message
     */
    public static String removeNewlines(String message) {
        if (message == null) {
            return null;
        }

        return message.replaceAll("[\\r\\n]", " ");
    }

    /**
     * Comprehensive log message sanitization (combines all methods)
     *
     * @param message The message to sanitize
     * @return Fully sanitized and safe log message
     */
    public static String sanitizeForLogging(String message) {
        if (message == null) {
            return null;
        }

        // Remove newlines (prevent log injection)
        String cleaned = removeNewlines(message);

        // Redact sensitive data
        cleaned = sanitize(cleaned);

        // Truncate to reasonable length
        cleaned = truncate(cleaned, 1000);

        return cleaned;
    }
}
