package com.prayer_chat.chatbot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security tests for HeadlessFetchService, especially CHROME_BIN path validation
 * and URL validation before any headless fetch.
 */
@DisplayName("HeadlessFetchService security tests")
class HeadlessFetchServiceSecurityTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "/usr/bin/chromium",
        "/usr/bin/chromium-browser",
        "/usr/local/bin/chromium"
    })
    @DisplayName("Accepts safe CHROME_BIN paths under /usr/")
    void isSafeChromeBinPath_acceptsSafePaths(String path) {
        assertTrue(HeadlessFetchService.isSafeChromeBinPath(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/tmp/chromium",
        "/home/user/chrome",
        "/opt/evil/chromium",
        "/usr/../etc/passwd",
        "/usr/bin/../../../tmp/evil",
        "relative/path",
        "-usr/bin/chromium",
        "",
        "   "
    })
    @DisplayName("Rejects unsafe or non-/usr/ CHROME_BIN paths")
    void isSafeChromeBinPath_rejectsUnsafePaths(String path) {
        assertFalse(HeadlessFetchService.isSafeChromeBinPath(path));
    }

    @Test
    @DisplayName("Rejects null CHROME_BIN")
    void isSafeChromeBinPath_rejectsNull() {
        assertFalse(HeadlessFetchService.isSafeChromeBinPath(null));
    }

    @Test
    @DisplayName("Rejects path with .. (traversal)")
    void isSafeChromeBinPath_rejectsPathTraversal() {
        assertFalse(HeadlessFetchService.isSafeChromeBinPath("/usr/bin/../evil"));
    }

    @Test
    @DisplayName("Rejects path over 256 chars")
    void isSafeChromeBinPath_rejectsTooLong() {
        String longPath = "/usr/bin/" + "a".repeat(300);
        assertFalse(HeadlessFetchService.isSafeChromeBinPath(longPath));
    }
}
