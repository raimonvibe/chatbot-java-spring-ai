package com.prayer_chat.chatbot.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for XssSanitizer
 * Tests XSS attack prevention and HTML sanitization
 */
@DisplayName("XSS Sanitizer Tests")
class XssSanitizerTest {

    private XssSanitizer xssSanitizer;

    @BeforeEach
    void setUp() {
        xssSanitizer = new XssSanitizer();
    }

    // ========== Basic Sanitization Tests ==========

    @Test
    @DisplayName("Should return null for null input")
    void shouldReturnNull_whenInputIsNull() {
        // Act
        String result = xssSanitizer.sanitize(null);

        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return empty string for empty input")
    void shouldReturnEmptyString_whenInputIsEmpty() {
        // Act
        String result = xssSanitizer.sanitize("");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should not modify safe text")
    void shouldNotModifySafeText() {
        // Arrange
        String safeText = "This is a safe text without any HTML";

        // Act
        String result = xssSanitizer.sanitize(safeText);

        // Assert
        assertThat(result).isEqualTo(safeText);
    }

    // ========== Script Tag Tests ==========

    @Test
    @DisplayName("Should remove script tags")
    void shouldRemoveScriptTags() {
        // Arrange
        String maliciousInput = "<script>alert('XSS')</script>";

        // Act
        String result = xssSanitizer.sanitize(maliciousInput);

        // Assert
        assertThat(result).isEmpty();
        assertThat(result).doesNotContain("<script>");
        assertThat(result).doesNotContain("alert");
    }

    @Test
    @DisplayName("Should remove script tags with attributes")
    void shouldRemoveScriptTags_withAttributes() {
        // Arrange
        String maliciousInput = "<script type='text/javascript'>alert('XSS')</script>";

        // Act
        String result = xssSanitizer.sanitize(maliciousInput);

        // Assert
        assertThat(result).isEmpty();
        assertThat(result).doesNotContain("<script");
    }

    @Test
    @DisplayName("Should remove script tags case insensitively")
    void shouldRemoveScriptTags_caseInsensitive() {
        // Arrange
        String maliciousInput = "<SCRIPT>alert('XSS')</SCRIPT>";

        // Act
        String result = xssSanitizer.sanitize(maliciousInput);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should preserve text around script tags")
    void shouldPreserveText_aroundScriptTags() {
        // Arrange
        String input = "Hello <script>alert('XSS')</script> World";

        // Act
        String result = xssSanitizer.sanitize(input);

        // Assert
        // JSoup normalizes whitespace (multiple spaces become single space) - this is safe behavior
        assertThat(result).isEqualTo("Hello World");
        assertThat(result).doesNotContain("<script>");
    }

    // ========== Iframe Tests ==========

    @Test
    @DisplayName("Should remove iframe tags")
    void shouldRemoveIframeTags() {
        // Arrange
        String maliciousInput = "<iframe src='http://evil.com'></iframe>";

        // Act
        String result = xssSanitizer.sanitize(maliciousInput);

        // Assert
        assertThat(result).isEmpty();
        assertThat(result).doesNotContain("<iframe");
    }

    @Test
    @DisplayName("Should remove iframe tags case insensitively")
    void shouldRemoveIframeTags_caseInsensitive() {
        // Arrange
        String maliciousInput = "<IFRAME src='http://evil.com'></IFRAME>";

        // Act
        String result = xssSanitizer.sanitize(maliciousInput);

        // Assert
        assertThat(result).isEmpty();
    }

    // ========== Object and Embed Tests ==========

    @Test
    @DisplayName("Should remove object tags")
    void shouldRemoveObjectTags() {
        // Arrange
        String maliciousInput = "<object data='malicious.swf'></object>";

        // Act
        String result = xssSanitizer.sanitize(maliciousInput);

        // Assert
        assertThat(result).isEmpty();
        assertThat(result).doesNotContain("<object");
    }

    @Test
    @DisplayName("Should remove embed tags")
    void shouldRemoveEmbedTags() {
        // Arrange
        String maliciousInput = "<embed src='malicious.swf'>";

        // Act
        String result = xssSanitizer.sanitize(maliciousInput);

        // Assert
        assertThat(result).isEmpty();
        assertThat(result).doesNotContain("<embed");
    }

    // ========== Event Handler Tests ==========

    @ParameterizedTest
    @ValueSource(strings = {
        "onclick=",
        "onload=",
        "onerror=",
        "onmouseover=",
        "onmouseout=",
        "onfocus=",
        "onblur="
    })
    @DisplayName("Should remove event handlers")
    void shouldRemoveEventHandlers(String eventHandler) {
        // Arrange
        String maliciousInput = "<div " + eventHandler + "\"alert('XSS')\">Test</div>";

        // Act
        String result = xssSanitizer.sanitize(maliciousInput);

        // Assert
        assertThat(result).doesNotContain(eventHandler);
    }

    @Test
    @DisplayName("Should remove onclick handler case insensitively")
    void shouldRemoveOnclickHandler_caseInsensitive() {
        // Arrange
        String maliciousInput = "<div ONCLICK=\"alert('XSS')\">Test</div>";

        // Act
        String result = xssSanitizer.sanitize(maliciousInput);

        // Assert
        assertThat(result).doesNotContain("ONCLICK");
        assertThat(result).doesNotContain("onclick");
    }

    // ========== Javascript Protocol Tests ==========

    @Test
    @DisplayName("Should remove javascript protocol")
    void shouldRemoveJavascriptProtocol() {
        // Arrange
        String maliciousInput = "<a href='javascript:alert(\"XSS\")'>Click me</a>";

        // Act
        String result = xssSanitizer.sanitize(maliciousInput);

        // Assert
        assertThat(result).doesNotContain("javascript:");
    }

    @Test
    @DisplayName("Should remove javascript protocol case insensitively")
    void shouldRemoveJavascriptProtocol_caseInsensitive() {
        // Arrange
        String maliciousInput = "<a href='JaVaScRiPt:alert(\"XSS\")'>Click me</a>";

        // Act
        String result = xssSanitizer.sanitize(maliciousInput);

        // Assert
        assertThat(result).doesNotContain("javascript:");
        assertThat(result).doesNotContain("JaVaScRiPt:");
    }

    // ========== VBScript Protocol Tests ==========

    @Test
    @DisplayName("Should remove vbscript protocol")
    void shouldRemoveVbscriptProtocol() {
        // Arrange
        String maliciousInput = "<a href='vbscript:msgbox(\"XSS\")'>Click me</a>";

        // Act
        String result = xssSanitizer.sanitize(maliciousInput);

        // Assert
        assertThat(result).doesNotContain("vbscript:");
    }

    @Test
    @DisplayName("Should remove vbscript protocol case insensitively")
    void shouldRemoveVbscriptProtocol_caseInsensitive() {
        // Arrange
        String maliciousInput = "<a href='VbScRiPt:msgbox(\"XSS\")'>Click me</a>";

        // Act
        String result = xssSanitizer.sanitize(maliciousInput);

        // Assert
        assertThat(result).doesNotContain("vbscript:");
        assertThat(result).doesNotContain("VbScRiPt:");
    }

    // ========== Complex Attack Tests ==========

    @Test
    @DisplayName("Should handle multiple attack vectors in one input")
    void shouldHandleMultipleAttackVectors() {
        // Arrange
        String maliciousInput =
            "Hello <script>alert('XSS')</script> " +
            "<iframe src='evil.com'></iframe> " +
            "<div onclick='steal()'>Click</div> " +
            "<a href='javascript:void(0)'>Link</a>";

        // Act
        String result = xssSanitizer.sanitize(maliciousInput);

        // Assert
        assertThat(result).doesNotContain("<script>");
        assertThat(result).doesNotContain("<iframe>");
        assertThat(result).doesNotContain("onclick");
        assertThat(result).doesNotContain("javascript:");
        assertThat(result).contains("Hello");
    }

    @Test
    @DisplayName("Should handle nested script tags")
    void shouldHandleNestedScriptTags() {
        // Arrange
        String maliciousInput = "<script><script>alert('XSS')</script></script>";

        // Act
        String result = xssSanitizer.sanitize(maliciousInput);

        // Assert
        assertThat(result).isEmpty();
    }

    // ========== Sanitize and Escape Tests ==========

    @Test
    @DisplayName("Should sanitize and HTML escape dangerous content")
    void shouldSanitizeAndEscape_dangerousContent() {
        // Arrange
        String input = "<script>alert('XSS')</script><p>Test & \"quote\"</p>";

        // Act
        String result = xssSanitizer.sanitizeAndEscape(input);

        // Assert
        assertThat(result).doesNotContain("<script>");
        assertThat(result).contains("&amp;"); // & should be escaped
        assertThat(result).contains("&quot;"); // " should be escaped
    }

    @Test
    @DisplayName("Should return null for null input in sanitizeAndEscape")
    void shouldReturnNull_whenSanitizeAndEscapeWithNullInput() {
        // Act
        String result = xssSanitizer.sanitizeAndEscape(null);

        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should HTML escape special characters")
    void shouldHtmlEscapeSpecialCharacters() {
        // Arrange
        String input = "Test & < > \" ' characters";

        // Act
        String result = xssSanitizer.sanitizeAndEscape(input);

        // Assert
        // JSoup removes HTML tags first, then StringEscapeUtils escapes remaining text
        // This results in safe escaping of all special characters
        // The exact escape format may vary, but dangerous characters must be escaped
        assertThat(result).contains("&amp;"); // & (escaped)
        
        // At minimum, ensure dangerous characters are escaped (not raw)
        // This is the most important security check
        assertThat(result).doesNotContain("<");
        assertThat(result).doesNotContain(">");
        assertThat(result).doesNotContain("\""); // Quote should be escaped
    }

    // ========== Dangerous Content Detection Tests ==========

    @Test
    @DisplayName("Should detect script tag as dangerous")
    void shouldDetectDangerousContent_scriptTag() {
        // Arrange
        String input = "<script>alert('XSS')</script>";

        // Act
        boolean isDangerous = xssSanitizer.containsDangerousContent(input);

        // Assert
        assertThat(isDangerous).isTrue();
    }

    @Test
    @DisplayName("Should detect iframe tag as dangerous")
    void shouldDetectDangerousContent_iframeTag() {
        // Arrange
        String input = "<iframe src='evil.com'></iframe>";

        // Act
        boolean isDangerous = xssSanitizer.containsDangerousContent(input);

        // Assert
        assertThat(isDangerous).isTrue();
    }

    @Test
    @DisplayName("Should detect event handler as dangerous")
    void shouldDetectDangerousContent_eventHandler() {
        // Arrange
        String input = "<div onclick='alert()'>Test</div>";

        // Act
        boolean isDangerous = xssSanitizer.containsDangerousContent(input);

        // Assert
        assertThat(isDangerous).isTrue();
    }

    @Test
    @DisplayName("Should detect javascript protocol as dangerous")
    void shouldDetectDangerousContent_javascriptProtocol() {
        // Arrange
        String input = "<a href='javascript:void(0)'>Link</a>";

        // Act
        boolean isDangerous = xssSanitizer.containsDangerousContent(input);

        // Assert
        assertThat(isDangerous).isTrue();
    }

    @Test
    @DisplayName("Should not detect safe content as dangerous")
    void shouldNotDetectDangerousContent_safeText() {
        // Arrange
        String input = "This is safe text with <b>bold</b> and <i>italic</i>";

        // Act
        boolean isDangerous = xssSanitizer.containsDangerousContent(input);

        // Assert
        assertThat(isDangerous).isFalse();
    }

    @Test
    @DisplayName("Should return false for null input in containsDangerousContent")
    void shouldReturnFalse_whenCheckingNullForDangerousContent() {
        // Act
        boolean isDangerous = xssSanitizer.containsDangerousContent(null);

        // Assert
        assertThat(isDangerous).isFalse();
    }

    @Test
    @DisplayName("Should return false for empty input in containsDangerousContent")
    void shouldReturnFalse_whenCheckingEmptyForDangerousContent() {
        // Act
        boolean isDangerous = xssSanitizer.containsDangerousContent("");

        // Assert
        assertThat(isDangerous).isFalse();
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should handle very long malicious input")
    void shouldHandleVeryLongMaliciousInput() {
        // Arrange
        String longMaliciousInput = "<script>" + "a".repeat(10000) + "</script>";

        // Act
        String result = xssSanitizer.sanitize(longMaliciousInput);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should handle unicode in script tags")
    void shouldHandleUnicodeInScriptTags() {
        // Arrange
        String maliciousInput = "<script>alert('XSS 攻击')</script>";

        // Act
        String result = xssSanitizer.sanitize(maliciousInput);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should handle multiline script content")
    void shouldHandleMultilineScriptContent() {
        // Arrange
        String maliciousInput = "<script>\n" +
                               "alert('line1');\n" +
                               "alert('line2');\n" +
                               "</script>";

        // Act
        String result = xssSanitizer.sanitize(maliciousInput);

        // Assert
        assertThat(result).isEmpty();
    }
}
