package com.prayer_chat.chatbot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for robots.txt parsing and crawl-allowed checks (aligns behaviour with privacy policy).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RobotsTxtService")
class RobotsTxtServiceTest {

    @Mock
    private UrlValidationService urlValidationService;

    private RobotsTxtService service;

    @BeforeEach
    void setUp() {
        service = new RobotsTxtService(urlValidationService);
        ReflectionTestUtils.setField(service, "userAgent", "PrayerChatCrawler/1.0 (+https://prayer-chat.com/bot)");
    }

    @Test
    @DisplayName("parseRobotsTxt uses most specific group (crawler token over *)")
    void parseRobotsTxt_prefersCrawlerTokenOverStar() {
        String content = "User-agent: *\nDisallow: /admin/\n\nUser-agent: PrayerChatCrawler\nAllow: /admin/public/\nDisallow: /admin/";
        RobotsTxtService.RobotsRules rules = RobotsTxtService.parseRobotsTxt(content, "PrayerChatCrawler");
        assertThat(rules).isNotNull();
        assertThat(rules.isAllowed("/")).isTrue();
        assertThat(rules.isAllowed("/admin/")).isFalse();
        assertThat(rules.isAllowed("/admin/public/")).isTrue();
        assertThat(rules.isAllowed("/admin/public/foo")).isTrue();
    }

    @Test
    @DisplayName("parseRobotsTxt falls back to * when no crawler-specific group")
    void parseRobotsTxt_fallsBackToStar() {
        String content = "User-agent: *\nDisallow: /private/";
        RobotsTxtService.RobotsRules rules = RobotsTxtService.parseRobotsTxt(content, "PrayerChatCrawler");
        assertThat(rules).isNotNull();
        assertThat(rules.isAllowed("/")).isTrue();
        assertThat(rules.isAllowed("/private/")).isFalse();
        assertThat(rules.isAllowed("/private/foo")).isFalse();
        assertThat(rules.isAllowed("/public")).isTrue();
    }

    @Test
    @DisplayName("parseRobotsTxt empty Disallow means allow all")
    void parseRobotsTxt_emptyDisallowAllowsAll() {
        String content = "User-agent: PrayerChatCrawler\nDisallow:";
        RobotsTxtService.RobotsRules rules = RobotsTxtService.parseRobotsTxt(content, "PrayerChatCrawler");
        assertThat(rules).isNotNull();
        assertThat(rules.isAllowed("/")).isTrue();
        assertThat(rules.isAllowed("/any/path")).isTrue();
    }

    @Test
    @DisplayName("getCrawlerToken extracts token from user-agent")
    void getCrawlerToken_extractsToken() {
        assertThat(service.getCrawlerToken()).isEqualTo("PrayerChatCrawler");
    }

    @Test
    @DisplayName("isCrawlAllowed returns false for null or blank URL")
    void isCrawlAllowed_rejectsNullOrBlank() {
        // No stub: null/blank are rejected before validation is called
        assertThat(service.isCrawlAllowed(null)).isFalse();
        assertThat(service.isCrawlAllowed("")).isFalse();
        assertThat(service.isCrawlAllowed("   ")).isFalse();
    }

    @Test
    @DisplayName("isCrawlAllowed returns false when URL validation fails")
    void isCrawlAllowed_respectsUrlValidation() {
        when(urlValidationService.isValidAndSafe("http://evil.internal/")).thenReturn(false);
        assertThat(service.isCrawlAllowed("http://evil.internal/")).isFalse();
    }

    @Test
    @DisplayName("isCrawlAllowed returns true when validation passes and no robots block (fetch failure)")
    void isCrawlAllowed_allowsWhenValidationPassesAndNoRobots() {
        when(urlValidationService.isValidAndSafe(anyString())).thenReturn(true);
        // Unknown host or unreachable origin -> fetch fails -> allow (fail open)
        assertThat(service.isCrawlAllowed("http://nonexistent-domain-12345.invalid/")).isTrue();
    }

    // ---------- Security tests ----------

    @Test
    @DisplayName("SECURITY: isCrawlAllowed validates URL before any fetch (SSRF)")
    void security_isCrawlAllowed_validatesUrlBeforeFetch() {
        when(urlValidationService.isValidAndSafe("http://evil.internal/")).thenReturn(false);
        assertThat(service.isCrawlAllowed("http://evil.internal/")).isFalse();
        verify(urlValidationService).isValidAndSafe(eq("http://evil.internal/"));
        // Should not be called again for robots.txt (we never fetch when URL is invalid)
        verify(urlValidationService).isValidAndSafe(anyString());
    }

    @Test
    @DisplayName("SECURITY: isCrawlAllowed rejects null/blank without calling validation")
    void security_isCrawlAllowed_rejectsNullOrBlankNoValidation() {
        service.isCrawlAllowed(null);
        service.isCrawlAllowed("");
        verify(urlValidationService, never()).isValidAndSafe(anyString());
    }

    @Test
    @DisplayName("SECURITY: parseRobotsTxt handles path with dot-dot (no injection)")
    void security_parseRobotsTxt_pathWithDotDot() {
        String content = "User-agent: *\nDisallow: /foo/../admin/";
        RobotsTxtService.RobotsRules rules = RobotsTxtService.parseRobotsTxt(content, "*");
        assertThat(rules).isNotNull();
        // Prefix match: /foo/../admin/ matches paths that start with that string
        assertThat(rules.isAllowed("/foo/../admin/")).isFalse();
        assertThat(rules.isAllowed("/admin/")).isTrue();
    }

    @Test
    @DisplayName("SECURITY: parseRobotsTxt null or blank content returns allow-all")
    void security_parseRobotsTxt_nullOrBlankContent() {
        RobotsTxtService.RobotsRules empty = RobotsTxtService.parseRobotsTxt("", "Bot");
        assertThat(empty).isNotNull();
        assertThat(empty.isAllowed("/")).isTrue();
        assertThat(empty.isAllowed("/any")).isTrue();
        RobotsTxtService.RobotsRules fromNull = RobotsTxtService.parseRobotsTxt(null, "Bot");
        assertThat(fromNull).isNotNull();
        assertThat(fromNull.isAllowed("/")).isTrue();
        RobotsTxtService.RobotsRules fromBlank = RobotsTxtService.parseRobotsTxt("   \n  ", "Bot");
        assertThat(fromBlank.isAllowed("/")).isTrue();
    }

    @Test
    @DisplayName("SECURITY: getCrawlerToken returns safe default when userAgent null/blank")
    void security_getCrawlerToken_safeDefault() {
        ReflectionTestUtils.setField(service, "userAgent", null);
        assertThat(service.getCrawlerToken()).isEqualTo("PrayerChatCrawler");
        ReflectionTestUtils.setField(service, "userAgent", "   ");
        assertThat(service.getCrawlerToken()).isEqualTo("PrayerChatCrawler");
    }

    @Test
    @DisplayName("SECURITY: malformed or invalid URL is rejected (no throw); exception path fails open")
    void security_isCrawlAllowed_invalidUrlRejectedNoThrow() {
        when(urlValidationService.isValidAndSafe("not-a-valid-url")).thenReturn(false);
        assertThat(service.isCrawlAllowed("not-a-valid-url")).isFalse();
    }
}
