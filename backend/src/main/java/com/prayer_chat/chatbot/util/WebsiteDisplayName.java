package com.prayer_chat.chatbot.util;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * Derives a human-friendly default chatbot name from a website URL.
 * <p>
 * Hosted platforms (e.g. {@code *.vercel.app}) often yield a useless label like "Vercel" when only
 * the registrable segment is used; we prefer the project slug from the hostname instead.
 */
public final class WebsiteDisplayName {

    private static final String SUFFIX = " Chatbot";
    /** Align with {@code ChatbotRequest} name max length */
    private static final int MAX_NAME_LENGTH = 100;

    /**
     * Hostnames where the meaningful label is the left part before the platform suffix
     * (e.g. {@code my-project.vercel.app} → {@code my-project}).
     */
    private static final Pattern PLATFORM_HOST = Pattern.compile(
        "(?i)^(.+)\\.(vercel\\.app|netlify\\.app|github\\.io|pages\\.dev|railway\\.app|onrender\\.com|"
            + "web\\.app|firebaseapp\\.com|appspot\\.com|azurewebsites\\.net|cloudfront\\.net)$"
    );

    private WebsiteDisplayName() {
    }

    public static String suggestedChatbotNameFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return "My Chatbot";
        }
        try {
            URI uri = new URI(url.trim());
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                return "My Chatbot";
            }
            host = host.toLowerCase();

            String withoutWww = host.replaceFirst("^www\\.", "");
            var platformMatcher = PLATFORM_HOST.matcher(withoutWww);
            if (platformMatcher.matches()) {
                String slug = stripHostDerivedNoise(platformMatcher.group(1));
                return fromSlug(slug);
            }

            return fromGenericHost(withoutWww);
        } catch (Exception e) {
            return "My Chatbot";
        }
    }

    private static String fromSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return "My Chatbot";
        }
        String title = titleCaseFromSlug(slug);
        if (title.isEmpty()) {
            return "My Chatbot";
        }
        return truncateToMaxLength(title + SUFFIX);
    }

    private static String fromGenericHost(String host) {
        String domain = host.replaceFirst("^www\\.", "");
        String[] labels = domain.split("\\.");

        if (labels.length == 0 || (labels.length == 1 && labels[0].isEmpty())) {
            return "My Chatbot";
        }

        String mainPart;
        if (labels.length == 2 && labels[1].length() == 2 && labels[1].matches("[a-z]+")) {
            // Second label is a typical two-letter ccTLD (e.g. brand.eu, brand.de, site.io). Do not use the TLD as the name.
            mainPart = labels[0];
        } else {
            String rest = domain.replaceFirst("\\.(com|org|net|edu|gov|co|io|ai|app|dev|eu|info|biz|name)$", "");
            String[] parts = rest.split("\\.");
            mainPart = parts.length > 0 ? parts[parts.length - 1] : rest;
        }

        mainPart = stripHostDerivedNoise(mainPart);
        if (mainPart == null || mainPart.isEmpty()) {
            return "My Chatbot";
        }
        String capitalized = mainPart.substring(0, 1).toUpperCase()
            + (mainPart.length() > 1 ? mainPart.substring(1).toLowerCase() : "");
        return truncateToMaxLength(capitalized + SUFFIX);
    }

    /**
     * Strip characters that must never appear in a derived display name (defense in depth alongside
     * {@link com.prayer_chat.chatbot.dto.ChatbotRequest} validation and client-side display sanitization).
     */
    private static String stripHostDerivedNoise(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return raw.codePoints()
            .filter(cp -> !Character.isISOControl(cp)
                && cp != '<' && cp != '>' && cp != '"' && cp != '\'' && cp != '\\')
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
    }

    static String titleCaseFromSlug(String slug) {
        String[] segments = slug.split("[-_]+");
        StringBuilder out = new StringBuilder();
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(segment.charAt(0)));
            if (segment.length() > 1) {
                out.append(segment.substring(1).toLowerCase());
            }
        }
        return out.toString().trim();
    }

    private static String truncateToMaxLength(String full) {
        if (full.length() <= MAX_NAME_LENGTH) {
            return full;
        }
        int maxTitleLen = MAX_NAME_LENGTH - SUFFIX.length();
        if (maxTitleLen < 1) {
            return full.substring(0, MAX_NAME_LENGTH);
        }
        String titlePart = full.substring(0, full.length() - SUFFIX.length());
        if (titlePart.length() <= maxTitleLen) {
            return full.substring(0, MAX_NAME_LENGTH);
        }
        String cut = titlePart.substring(0, maxTitleLen);
        int lastSpace = cut.lastIndexOf(' ');
        if (lastSpace > maxTitleLen / 2) {
            cut = cut.substring(0, lastSpace);
        }
        return cut.trim() + SUFFIX;
    }
}
