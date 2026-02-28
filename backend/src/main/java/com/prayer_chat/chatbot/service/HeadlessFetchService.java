package com.prayer_chat.chatbot.service;

import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Semaphore;

/**
 * Fetches a URL using a headless Chrome browser so that client-rendered (SPA) pages
 * are fully rendered and we can extract content. Used only when Jsoup returns minimal
 * content (e.g. React/Next/Vercel apps).
 * <p>
 * Security: Callers must validate the URL with {@link UrlValidationService} before
 * calling. This service does not perform network calls until the URL is used; it
 * assumes the URL has already been validated for SSRF.
 * <p>
 * Safe use: timeouts, no persistent profile, headless only. If Chrome/Chromium is not
 * installed (e.g. minimal Docker image), returns empty and the crawler falls back to
 * Jsoup-only content.
 */
@Service
public class HeadlessFetchService {

    private static final Logger logger = LoggerFactory.getLogger(HeadlessFetchService.class);

    @Value("${app.website-analysis.headless-timeout-seconds:25}")
    private int headlessTimeoutSeconds;

    @Value("${app.website-analysis.headless-enabled:true}")
    private boolean headlessEnabled;

    private final UrlValidationService urlValidationService;
    /** Only one headless browser at a time to avoid OOM on small instances (e.g. Render). */
    private final Semaphore headlessPermits = new Semaphore(1);

    public HeadlessFetchService(UrlValidationService urlValidationService) {
        this.urlValidationService = urlValidationService;
    }

    /**
     * Fetch the URL with headless Chrome and return the rendered HTML.
     * SECURITY: Call only with URLs that have already passed {@link UrlValidationService#isValidAndSafe(String)}.
     *
     * @param url Already-validated URL to fetch (same origin as the crawl).
     * @return Rendered page HTML, or empty if headless is disabled, Chrome unavailable, or timeout.
     */
    public Optional<String> fetchRenderedHtml(String url) {
        if (!headlessEnabled) {
            logger.debug("Headless fetch disabled by config");
            return Optional.empty();
        }
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        // Double-check: do not fetch if URL was not validated (defense in depth)
        if (!urlValidationService.isValidAndSafe(url)) {
            logger.warn("Headless fetch skipped: URL not valid (SSRF)");
            return Optional.empty();
        }

        // Only one headless browser at a time to avoid memory exhaustion (Chromium is ~200–400MB each)
        if (!headlessPermits.tryAcquire()) {
            logger.debug("Headless fetch skipped: another headless browser in use (memory limit)");
            return Optional.empty();
        }

        WebDriver driver = null;
        try {
            ChromeOptions options = new ChromeOptions();
            // Use CHROME_BIN only when set and path is safe (prevents path injection via env)
            String chromeBin = System.getenv("CHROME_BIN");
            if (isSafeChromeBinPath(chromeBin)) {
                options.setBinary(chromeBin);
            }
            // Hardened flags: headless, no sandbox (required in Docker), minimal surface
            options.addArguments(
                "--headless=new",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--disable-software-rasterizer",
                "--window-size=1280,720",
                "--disable-extensions",
                "--disable-setuid-sandbox",
                "--no-first-run",
                "--disable-background-networking",
                "--disable-default-apps",
                "--disable-sync",
                "--disable-translate",
                "--disable-features=TranslateUI",
                "--metrics-recording-only"
            );
            options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
            options.addArguments("--user-agent=PrayerChatCrawler/1.0 (+https://prayer-chat.com/bot)");

            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(headlessTimeoutSeconds));
            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(10));

            driver.get(url);

            // Allow a short time for SPA hydration (e.g. React/Next)
            Thread.sleep(2000);

            String html = driver.getPageSource();
            if (html != null && !html.isBlank()) {
                return Optional.of(html);
            }
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Headless fetch interrupted for {}", url);
            return Optional.empty();
        } catch (Exception e) {
            logger.debug("Headless fetch failed for {}: {} (Chrome may not be installed)", url, e.getMessage());
            return Optional.empty();
        } finally {
            headlessPermits.release();
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    logger.debug("Error closing headless driver: {}", e.getMessage());
                }
            }
        }
    }

    public boolean isHeadlessEnabled() {
        return headlessEnabled;
    }

    /**
     * Only allow CHROME_BIN to point to a known safe location (e.g. /usr/bin/chromium).
     * Rejects path traversal, relative paths, and paths outside system browser dirs.
     * Package visibility for tests.
     */
    static boolean isSafeChromeBinPath(String path) {
        if (path == null || path.isBlank()) return false;
        String p = path.trim();
        if (p.contains("..") || p.startsWith("-")) return false;
        // Allow only absolute paths under /usr (typical for apt-installed Chromium)
        return p.startsWith("/usr/") && p.length() <= 256;
    }
}
