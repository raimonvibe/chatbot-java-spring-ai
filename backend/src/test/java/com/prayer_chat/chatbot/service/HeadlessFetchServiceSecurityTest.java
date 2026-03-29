package com.prayer_chat.chatbot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security tests for HeadlessFetchService, especially CHROME_BIN path validation,
 * Chrome temp dir path validation, and URL validation before any headless fetch.
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

    // ---------- Chrome temp dir (user-data-dir) path security ----------

    @Test
    @DisplayName("createChromeTempDirUnder returns path under given tmp base")
    void createChromeTempDirUnder_returnsPathUnderTmp(@TempDir Path tmpDir) {
        Optional<Path> result = HeadlessFetchService.createChromeTempDirUnder(tmpDir.toString());
        assertTrue(result.isPresent());
        Path path = result.get().normalize();
        Path base = tmpDir.toAbsolutePath().normalize();
        assertTrue(path.startsWith(base), "Resolved path must be under tmp base");
        assertTrue(path.getFileName().toString().startsWith("chrome-headless-"));
    }

    @Test
    @DisplayName("createChromeTempDirUnder rejects null or blank tmp base")
    void createChromeTempDirUnder_rejectsNullOrBlank() {
        assertTrue(HeadlessFetchService.createChromeTempDirUnder(null).isEmpty());
        assertTrue(HeadlessFetchService.createChromeTempDirUnder("").isEmpty());
        assertTrue(HeadlessFetchService.createChromeTempDirUnder("   ").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = { "/tmp/../../../etc", "/tmp/..", "/nonexistent/../etc" })
    @DisplayName("createChromeTempDirUnder resolves and keeps path under normalized base")
    void createChromeTempDirUnder_normalizesAndRestrictsToBase(String tmpBase) {
        Optional<Path> result = HeadlessFetchService.createChromeTempDirUnder(tmpBase);
        if (result.isPresent()) {
            Path base = Path.of(tmpBase).toAbsolutePath().normalize();
            assertTrue(result.get().normalize().startsWith(base));
        }
    }

    @Test
    @DisplayName("deleteRecursively only deletes under baseDir")
    void deleteRecursively_onlyDeletesUnderBase(@TempDir Path tmpDir) throws IOException {
        Path under = tmpDir.resolve("chrome-headless-123");
        Files.createDirectories(under);
        Files.writeString(under.resolve("file.txt"), "x");
        File underFile = under.toFile();
        File baseFile = tmpDir.toFile();
        HeadlessFetchService.deleteRecursively(underFile, baseFile);
        assertFalse(Files.exists(under));
        assertTrue(Files.exists(tmpDir));
    }

    @Test
    @DisplayName("deleteRecursively does not delete when dir is outside baseDir")
    void deleteRecursively_doesNotDeleteWhenOutsideBase(@TempDir Path tmpDir) throws IOException {
        Path outside = tmpDir.resolve("a").getParent().resolve("other").resolve("dir");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("file.txt"), "x");
        File outsideFile = outside.toFile();
        File baseFile = tmpDir.resolve("a").toFile();
        HeadlessFetchService.deleteRecursively(outsideFile, baseFile);
        assertTrue(Files.exists(outside));
        Files.deleteIfExists(outside.resolve("file.txt"));
        Files.deleteIfExists(outside);
    }

    @Test
    @DisplayName("deleteRecursively no-op for null dir or null base")
    void deleteRecursively_noOpForNull() throws IOException {
        HeadlessFetchService.deleteRecursively(null, new File(System.getProperty("java.io.tmpdir")));
        HeadlessFetchService.deleteRecursively(new File(System.getProperty("java.io.tmpdir")), null);
    }
}
