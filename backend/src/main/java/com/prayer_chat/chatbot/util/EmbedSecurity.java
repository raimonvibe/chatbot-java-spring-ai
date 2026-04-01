package com.prayer_chat.chatbot.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.regex.Pattern;

/**
 * Security helpers for the embeddable widget flow.
 * <ul>
 *   <li>Base URL: only allow http(s) URLs from config; never user input (SSRF prevention).</li>
 *   <li>Escape for use inside JavaScript string literals to prevent XSS in generated embed code.</li>
 *   <li>Public embed config: restrict to allowed keys and safe value patterns.</li>
 * </ul>
 */
public final class EmbedSecurity {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Allowed branding keys and safe value patterns (strict hex colors only). */
    private static final Pattern SAFE_COLOR = Pattern.compile("^#([A-Fa-f0-9]{3}|[A-Fa-f0-9]{6})$");
    private static final Pattern SAFE_FONT_FAMILY = Pattern.compile("^[a-zA-Z0-9\\s,_-]+$");
    private static final Pattern SAFE_BORDER_RADIUS = Pattern.compile("^[0-9]+(px|em|rem)?$");
    private static final int MAX_STRING_LENGTH = 200;
    /** Max raw JSON length before parse to prevent JSON bomb / DoS. */
    private static final int MAX_BRANDING_CONFIG_LENGTH = 4096;

    /** Allowed base URL: scheme http or https, then host (domain or IP), optional port and path. No query/fragment to reduce surface. */
    private static final Pattern SAFE_BASE_URL = Pattern.compile(
        "^(https?)://([a-zA-Z0-9][-a-zA-Z0-9.]*(?::[0-9]{1,5})?)(/[a-zA-Z0-9._/-]*)?$",
        Pattern.CASE_INSENSITIVE
    );

    /** Characters that must not appear in a value we inject into a JS string (single-quoted). */
    private static final Pattern UNSAFE_JS = Pattern.compile("[\"'\\\\\\r\\n<>]");

    /** Hardcoded fallback when both baseUrl and defaultUrl are invalid; never user or config controlled. */
    private static final String INTERNAL_FALLBACK_BASE_URL = "https://chatbot-java-spring-ai.onrender.com";

    private EmbedSecurity() {}

    /**
     * Validates and returns a base URL safe for use in embed code.
     * Rejects empty, malformed, or script-injectable values. If the provided defaultUrl
     * is itself invalid, returns a hardcoded safe fallback (defense in depth).
     *
     * @param baseUrl configured base URL (e.g. from app.base-url)
     * @param defaultUrl fallback if baseUrl is invalid (e.g. production backend URL)
     * @return validated URL without trailing slash; never returns an unvalidated URL
     */
    public static String validateAndNormalizeBaseUrl(String baseUrl, String defaultUrl) {
        String safeDefault = isSafeBaseUrl(defaultUrl) ? normalizeNoTrailingSlash(defaultUrl.trim()) : INTERNAL_FALLBACK_BASE_URL;
        if (baseUrl == null || baseUrl.isBlank()) {
            return safeDefault;
        }
        String trimmed = baseUrl.trim();
        if (!SAFE_BASE_URL.matcher(trimmed).matches()) {
            return safeDefault;
        }
        if (UNSAFE_JS.matcher(trimmed).find()) {
            return safeDefault;
        }
        return normalizeNoTrailingSlash(trimmed);
    }

    /** Returns true if the string is a safe base URL (pattern and no unsafe JS chars). */
    private static boolean isSafeBaseUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String t = url.trim();
        return SAFE_BASE_URL.matcher(t).matches() && !UNSAFE_JS.matcher(t).find();
    }

    /**
     * Escapes a string for safe inclusion inside a JavaScript single-quoted string.
     * Handles backslash, single quote, and newlines so the generated script cannot break out.
     */
    public static String escapeForJsString(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }

    private static String normalizeNoTrailingSlash(String url) {
        if (url == null || url.isEmpty()) return url;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Sanitize brandingConfig JSON for public embed API. Returns a JSON object with only
     * allowed keys (primaryColor, secondaryColor, fontFamily, borderRadius) and values
     * that match safe patterns to prevent XSS when the widget applies them.
     */
    public static String sanitizeBrandingConfig(String brandingConfig) {
        if (brandingConfig == null || brandingConfig.isBlank()) {
            return "{}";
        }
        if (brandingConfig.length() > MAX_BRANDING_CONFIG_LENGTH) {
            return "{}";
        }
        try {
            JsonNode root = JSON.readTree(brandingConfig);
            if (!root.isObject()) return "{}";
            ObjectNode out = JSON.createObjectNode();
            JsonNode pc = root.get("primaryColor");
            if (pc != null && pc.isTextual() && pc.asText().length() <= MAX_STRING_LENGTH
                && SAFE_COLOR.matcher(pc.asText().trim()).matches()) {
                out.put("primaryColor", pc.asText().trim());
            }
            JsonNode sc = root.get("secondaryColor");
            if (sc != null && sc.isTextual() && sc.asText().length() <= MAX_STRING_LENGTH
                && SAFE_COLOR.matcher(sc.asText().trim()).matches()) {
                out.put("secondaryColor", sc.asText().trim());
            }
            JsonNode ff = root.get("fontFamily");
            if (ff != null && ff.isTextual() && ff.asText().length() <= MAX_STRING_LENGTH
                && SAFE_FONT_FAMILY.matcher(ff.asText().trim()).matches()) {
                out.put("fontFamily", ff.asText().trim());
            }
            JsonNode br = root.get("borderRadius");
            if (br != null) {
                String v = br.isTextual() ? br.asText().trim() : String.valueOf(br.asInt());
                if (v.length() <= 20 && SAFE_BORDER_RADIUS.matcher(v).matches()) {
                    out.put("borderRadius", v);
                }
            }
            return JSON.writeValueAsString(out);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** Strip angle brackets from text to prevent script delivery in embed config (defense in depth). */
    public static String stripAngleBrackets(String text) {
        if (text == null) return "";
        return text.replace("<", "").replace(">", "");
    }

    /** Allowed avatar ids for chatbot avatar images (no path traversal / injection). */
    private static final java.util.Set<String> ALLOWED_AVATAR_IDS = java.util.Set.of("1", "2", "3", "4", "5", "6");

    /**
     * Validates avatar id for storage and embed API. Returns null if input is null, blank, or not in 1-6.
     */
    public static String validateAvatarId(String avatarId) {
        if (avatarId == null || avatarId.isBlank()) return null;
        String trimmed = avatarId.trim();
        return ALLOWED_AVATAR_IDS.contains(trimmed) ? trimmed : null;
    }
}
