package com.prayer_chat.chatbot.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security tests for embed flow: base URL validation, JS escaping, branding sanitization, and output safety.
 */
@DisplayName("EmbedSecurity tests")
class EmbedSecurityTest {

    private static final String DEFAULT_URL = "https://chatbot-backend-4mp4.onrender.com";

    @Nested
    @DisplayName("validateAndNormalizeBaseUrl")
    class ValidateBaseUrl {

        @Test
        @DisplayName("Accepts valid HTTPS URL and removes trailing slash")
        void acceptsValidHttps() {
            assertThat(EmbedSecurity.validateAndNormalizeBaseUrl("https://api.example.com/", DEFAULT_URL))
                .isEqualTo("https://api.example.com");
        }

        @Test
        @DisplayName("Accepts valid HTTP URL for local dev")
        void acceptsValidHttp() {
            assertThat(EmbedSecurity.validateAndNormalizeBaseUrl("http://localhost:8081", DEFAULT_URL))
                .isEqualTo("http://localhost:8081");
        }

        @Test
        @DisplayName("Accepts URL with path")
        void acceptsUrlWithPath() {
            assertThat(EmbedSecurity.validateAndNormalizeBaseUrl("https://api.example.com/v1", DEFAULT_URL))
                .isEqualTo("https://api.example.com/v1");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "javascript:alert(1)",
            "file:///etc/passwd",
            "data:text/html,<script>alert(1)</script>",
            "https://evil.com'",
            "https://evil.com\"",
            "https://evil.com<x>",
            "https://evil.com\\path"
        })
        @DisplayName("Rejects dangerous or script-injectable URLs and returns default")
        void rejectsDangerousUrls(String input) {
            String result = EmbedSecurity.validateAndNormalizeBaseUrl(input, DEFAULT_URL);
            assertThat(result).as("input=%s", input).isEqualTo(DEFAULT_URL);
            assertThat(result).doesNotContain("javascript:");
            assertThat(result).doesNotContain("</script>");
            assertThat(result).doesNotContain("alert(");
        }

        @Test
        @DisplayName("Null or blank returns default")
        void nullOrBlankReturnsDefault() {
            assertThat(EmbedSecurity.validateAndNormalizeBaseUrl(null, DEFAULT_URL)).isEqualTo(DEFAULT_URL);
            assertThat(EmbedSecurity.validateAndNormalizeBaseUrl("", DEFAULT_URL)).isEqualTo(DEFAULT_URL);
            assertThat(EmbedSecurity.validateAndNormalizeBaseUrl("   ", DEFAULT_URL)).isEqualTo(DEFAULT_URL);
        }

        @Test
        @DisplayName("Invalid defaultUrl is not used; internal fallback is returned")
        void invalidDefaultUsesInternalFallback() {
            String internalFallback = "https://chatbot-java-spring-ai.onrender.com";
            assertThat(EmbedSecurity.validateAndNormalizeBaseUrl(null, "javascript:alert(1)")).isEqualTo(internalFallback);
            assertThat(EmbedSecurity.validateAndNormalizeBaseUrl("", "https://evil.com'")).isEqualTo(internalFallback);
            assertThat(EmbedSecurity.validateAndNormalizeBaseUrl("  ", "not-a-url")).isEqualTo(internalFallback);
        }
    }

    @Nested
    @DisplayName("escapeForJsString")
    class EscapeForJs {

        @Test
        @DisplayName("Escapes single quote so script cannot break out")
        void escapesSingleQuote() {
            assertThat(EmbedSecurity.escapeForJsString("https://example.com/path'with"))
                .isEqualTo("https://example.com/path\\'with");
        }

        @Test
        @DisplayName("Escapes backslash first to avoid double-escape issues")
        void escapesBackslash() {
            assertThat(EmbedSecurity.escapeForJsString("a\\b")).isEqualTo("a\\\\b");
        }

        @Test
        @DisplayName("Escapes double quote")
        void escapesDoubleQuote() {
            assertThat(EmbedSecurity.escapeForJsString("say \"hi\"")).isEqualTo("say \\\"hi\\\"");
        }

        @Test
        @DisplayName("Escapes newlines")
        void escapesNewlines() {
            assertThat(EmbedSecurity.escapeForJsString("a\nb\r\nc"))
                .contains("\\n").contains("\\r");
        }

        @Test
        @DisplayName("Null returns empty string")
        void nullReturnsEmpty() {
            assertThat(EmbedSecurity.escapeForJsString(null)).isEmpty();
        }

        @Test
        @DisplayName("Safe string unchanged")
        void safeStringUnchanged() {
            String safe = "https://chatbot-backend-4mp4.onrender.com";
            assertThat(EmbedSecurity.escapeForJsString(safe)).isEqualTo(safe);
        }
    }

    @Nested
    @DisplayName("sanitizeBrandingConfig")
    class SanitizeBranding {

        @Test
        @DisplayName("Allows only safe keys and value patterns")
        void allowsOnlySafeKeys() {
            String input = "{\"primaryColor\":\"#ff0000\",\"secondaryColor\":\"blue\",\"fontFamily\":\"Arial, sans-serif\",\"borderRadius\":\"8px\"}";
            String out = EmbedSecurity.sanitizeBrandingConfig(input);
            assertThat(out).contains("#ff0000").contains("blue").contains("Arial").contains("8px");
            assertThat(out).doesNotContain("script").doesNotContain("onerror").doesNotContain("javascript");
        }

        @Test
        @DisplayName("Rejects script injection in color value")
        void rejectsScriptInColor() {
            String input = "{\"primaryColor\":\"#ff0000</script><script>alert(1)</script>\"}";
            String out = EmbedSecurity.sanitizeBrandingConfig(input);
            assertThat(out).isEqualTo("{}");
        }

        @Test
        @DisplayName("Rejects unknown keys")
        void rejectsUnknownKeys() {
            String input = "{\"primaryColor\":\"#fff\",\"dangerous\":\"<img onerror=alert(1)>\"}";
            String out = EmbedSecurity.sanitizeBrandingConfig(input);
            assertThat(out).contains("primaryColor").doesNotContain("dangerous").doesNotContain("onerror");
        }

        @Test
        @DisplayName("Rejects invalid color format")
        void rejectsInvalidColor() {
            String input = "{\"primaryColor\":\"rgb(255,0,0)\"}";
            String out = EmbedSecurity.sanitizeBrandingConfig(input);
            assertThat(out).doesNotContain("primaryColor").isEqualTo("{}");
        }

        @Test
        @DisplayName("Null or blank returns empty object")
        void nullOrBlankReturnsEmpty() {
            assertThat(EmbedSecurity.sanitizeBrandingConfig(null)).isEqualTo("{}");
            assertThat(EmbedSecurity.sanitizeBrandingConfig("")).isEqualTo("{}");
            assertThat(EmbedSecurity.sanitizeBrandingConfig("   ")).isEqualTo("{}");
        }

        @Test
        @DisplayName("Oversized config returns empty object (DoS prevention)")
        void oversizedReturnsEmpty() {
            StringBuilder sb = new StringBuilder(5000);
            sb.append("{\"primaryColor\":\"#fff\"");
            for (int i = 0; i < 5000; i++) sb.append(",").append("\"x").append(i).append("\":\"y\"");
            sb.append("}");
            String out = EmbedSecurity.sanitizeBrandingConfig(sb.toString());
            assertThat(out).isEqualTo("{}");
        }

        @Test
        @DisplayName("Invalid JSON returns empty object")
        void invalidJsonReturnsEmpty() {
            assertThat(EmbedSecurity.sanitizeBrandingConfig("not json")).isEqualTo("{}");
            assertThat(EmbedSecurity.sanitizeBrandingConfig("{]")).isEqualTo("{}");
        }
    }

    @Nested
    @DisplayName("stripAngleBrackets")
    class StripAngleBrackets {

        @Test
        @DisplayName("Removes angle brackets to prevent script delivery")
        void removesAngleBrackets() {
            assertThat(EmbedSecurity.stripAngleBrackets("<script>alert(1)</script>"))
                .isEqualTo("scriptalert(1)/script");
        }

        @Test
        @DisplayName("Safe text unchanged")
        void safeTextUnchanged() {
            assertThat(EmbedSecurity.stripAngleBrackets("Church of Example")).isEqualTo("Church of Example");
        }

        @Test
        @DisplayName("Null returns empty string")
        void nullReturnsEmpty() {
            assertThat(EmbedSecurity.stripAngleBrackets(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("validateAvatarId")
    class ValidateAvatarId {

        @ParameterizedTest
        @ValueSource(strings = { "1", "2", "3", "4", "5", "6" })
        @DisplayName("Accepts allowed avatar ids 1-6")
        void acceptsAllowedIds(String id) {
            assertThat(EmbedSecurity.validateAvatarId(id)).isEqualTo(id);
        }

        @Test
        @DisplayName("Null or blank returns null")
        void nullOrBlankReturnsNull() {
            assertThat(EmbedSecurity.validateAvatarId(null)).isNull();
            assertThat(EmbedSecurity.validateAvatarId("")).isNull();
            assertThat(EmbedSecurity.validateAvatarId("   ")).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = { "0", "7", "10", "../1", "1;", "1'", "1.png", "a", "01" })
        @DisplayName("Rejects invalid or path-traversal values")
        void rejectsInvalid(String input) {
            assertThat(EmbedSecurity.validateAvatarId(input)).isNull();
        }
    }
}
