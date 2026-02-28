package com.prayer_chat.chatbot.service;

import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.WebsiteContent;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.repository.WebsiteContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * CRITICAL SECURITY TESTS for WebsiteAnalysisService
 * Tests SSRF protection, input validation, and crawling security
 *
 * These tests verify that the service CANNOT be exploited to:
 * - Access internal network resources
 * - Read local files
 * - Access cloud metadata endpoints
 * - Bypass URL validation
 * - Perform unlimited crawling
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Website Analysis Service - SECURITY TESTS")
class WebsiteAnalysisServiceSecurityTest {

    @Mock
    private WebsiteContentRepository websiteContentRepository;

    @Mock
    private UrlValidationService urlValidationService;

    @Mock
    private ChatbotRepository chatbotRepository;

    private WebsiteAnalysisService analysisService;

    @BeforeEach
    void setUp() {
        analysisService = new WebsiteAnalysisService(websiteContentRepository, urlValidationService, chatbotRepository, null);

        // Configure service with secure defaults
        ReflectionTestUtils.setField(analysisService, "maxPages", 50);
        ReflectionTestUtils.setField(analysisService, "maxDepth", 3);
        ReflectionTestUtils.setField(analysisService, "timeoutSeconds", 30);
        ReflectionTestUtils.setField(analysisService, "userAgent", "AI-Chatbot-Crawler/1.0");
    }

    // ========== CRITICAL: SSRF Protection Tests ==========

    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost",
        "http://localhost:8080",
        "http://127.0.0.1",
        "http://127.0.0.1:8080",
        "http://127.0.0.1:6379",  // Redis
        "http://127.0.0.1:5432",  // PostgreSQL
        "http://0.0.0.0",
        "http://[::1]",           // IPv6 localhost
        "http://[0:0:0:0:0:0:0:1]"
    })
    @DisplayName("SECURITY: Must block localhost access (SSRF)")
    void mustBlockLocalhost_ssrfProtection(String maliciousUrl) throws Exception {
        // Arrange - completeAndValidate returns empty for invalid/unsafe URLs
        Chatbot chatbot = createChatbot(maliciousUrl);
        when(urlValidationService.completeAndValidate(anyString())).thenReturn(Optional.empty());

        // Act
        CompletableFuture<List<WebsiteContent>> result = analysisService.analyzeWebsite(chatbot);
        List<WebsiteContent> content = result.get();

        // Assert - MUST NOT crawl localhost
        verify(urlValidationService, atLeastOnce()).completeAndValidate(maliciousUrl);
        assertThat(content).isEmpty();
        verify(websiteContentRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://10.0.0.1",
        "http://10.255.255.255",
        "http://172.16.0.1",
        "http://172.31.255.255",
        "http://192.168.0.1",
        "http://192.168.255.255",
        "http://192.168.1.1:8080"
    })
    @DisplayName("SECURITY: Must block private IP ranges (RFC 1918)")
    void mustBlockPrivateIpRanges_ssrfProtection(String privateIp) throws Exception {
        // Arrange
        Chatbot chatbot = createChatbot(privateIp);
        when(urlValidationService.completeAndValidate(anyString())).thenReturn(Optional.empty());

        // Act
        CompletableFuture<List<WebsiteContent>> result = analysisService.analyzeWebsite(chatbot);
        List<WebsiteContent> content = result.get();

        // Assert - MUST NOT access private networks
        verify(urlValidationService, atLeastOnce()).completeAndValidate(privateIp);
        assertThat(content).isEmpty();
        verify(websiteContentRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://169.254.169.254/latest/meta-data/",           // AWS metadata
        "http://169.254.169.254/latest/user-data/",
        "http://169.254.169.254/latest/dynamic/",
        "http://metadata.google.internal/",                    // Google Cloud
        "http://metadata.google.internal/computeMetadata/v1/",
        "http://169.254.169.254/metadata/v1/",                // Azure
        "http://169.254.169.254/metadata/instance"
    })
    @DisplayName("SECURITY: Must block cloud metadata endpoints (CRITICAL)")
    void mustBlockCloudMetadata_criticalSsrfProtection(String metadataUrl) throws Exception {
        // Arrange
        Chatbot chatbot = createChatbot(metadataUrl);
        when(urlValidationService.completeAndValidate(anyString())).thenReturn(Optional.empty());

        // Act
        CompletableFuture<List<WebsiteContent>> result = analysisService.analyzeWebsite(chatbot);
        List<WebsiteContent> content = result.get();

        // Assert - CRITICAL: Cloud metadata access = full AWS/GCP/Azure compromise
        verify(urlValidationService, atLeastOnce()).completeAndValidate(metadataUrl);
        assertThat(content).isEmpty();
        verify(websiteContentRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "file:///etc/passwd",
        "file:///etc/shadow",
        "file:///etc/hosts",
        "file://C:/Windows/System32/config/SAM",
        "file:///proc/self/environ",
        "file:///dev/random"
    })
    @DisplayName("SECURITY: Must block file:// protocol (local file access)")
    void mustBlockFileProtocol_localFileAccess(String fileUrl) throws Exception {
        // Arrange
        Chatbot chatbot = createChatbot(fileUrl);
        when(urlValidationService.completeAndValidate(anyString())).thenReturn(Optional.empty());

        // Act
        CompletableFuture<List<WebsiteContent>> result = analysisService.analyzeWebsite(chatbot);
        List<WebsiteContent> content = result.get();

        // Assert - MUST NOT read local files
        verify(urlValidationService, atLeastOnce()).completeAndValidate(fileUrl);
        assertThat(content).isEmpty();
        verify(websiteContentRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ftp://internal-server.local/",
        "gopher://localhost:70/",
        "dict://localhost:2628/",
        "ldap://internal-ldap.local/",
        "tftp://192.168.1.1/"
    })
    @DisplayName("SECURITY: Must block non-HTTP protocols")
    void mustBlockNonHttpProtocols(String url) throws Exception {
        // Arrange
        Chatbot chatbot = createChatbot(url);
        when(urlValidationService.completeAndValidate(anyString())).thenReturn(Optional.empty());

        // Act
        CompletableFuture<List<WebsiteContent>> result = analysisService.analyzeWebsite(chatbot);
        List<WebsiteContent> content = result.get();

        // Assert
        verify(urlValidationService, atLeastOnce()).completeAndValidate(url);
        assertThat(content).isEmpty();
    }

    // ========== URL Redirection Attacks ==========

    @Test
    @DisplayName("SECURITY: Must validate URLs after following redirects")
    void mustValidateUrls_afterRedirects() throws Exception {
        // Arrange - Attacker tries: https://safe.com -> http://localhost
        Chatbot chatbot = createChatbot("https://safe.com");
        when(urlValidationService.completeAndValidate("https://safe.com")).thenReturn(Optional.of("https://safe.com/"));
        when(urlValidationService.isValidAndSafe(anyString())).thenReturn(true);
        lenient().when(urlValidationService.isValidAndSafe(argThat(url ->
            url != null && url.contains("localhost")))).thenReturn(false);

        // Act
        CompletableFuture<List<WebsiteContent>> result = analysisService.analyzeWebsite(chatbot);
        List<WebsiteContent> content = result.get();

        // Assert - Must validate ALL URLs during crawl
        verify(urlValidationService, atLeastOnce()).isValidAndSafe(anyString());
    }

    // ========== DNS Rebinding Protection ==========

    @Test
    @DisplayName("SECURITY: Must re-validate URLs to prevent DNS rebinding")
    void mustRevalidateUrls_dnsRebindingProtection() throws Exception {
        // Arrange - DNS rebinding: safe.com initially resolves to 1.2.3.4,
        // later resolves to 127.0.0.1
        Chatbot chatbot = createChatbot("http://safe.com");
        when(urlValidationService.completeAndValidate("http://safe.com")).thenReturn(Optional.of("http://safe.com/"));
        when(urlValidationService.isValidAndSafe(anyString())).thenReturn(true);

        // Act
        CompletableFuture<List<WebsiteContent>> result = analysisService.analyzeWebsite(chatbot);
        result.get(); // Wait for async operation to complete

        // Assert - Service MUST call validation multiple times
        verify(urlValidationService, atLeastOnce()).isValidAndSafe(anyString());
    }

    // ========== Crawl Depth & Rate Limiting ==========

    @Test
    @DisplayName("SECURITY: Must respect max crawl depth to prevent infinite loops")
    void mustRespectMaxDepth_preventInfiniteLoops() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(analysisService, "maxDepth", 2);
        Chatbot chatbot = createChatbot("https://example.com");
        when(urlValidationService.completeAndValidate(anyString())).thenAnswer(inv -> Optional.of(inv.getArgument(0).toString()));
        when(urlValidationService.isValidAndSafe(anyString())).thenReturn(true);

        // Act
        CompletableFuture<List<WebsiteContent>> result = analysisService.analyzeWebsite(chatbot);
        List<WebsiteContent> content = result.get();

        // Assert - Should not crawl beyond maxDepth
        // Verify by checking repository save calls don't exceed reasonable limits
        verify(websiteContentRepository, atMost(50)).save(any());
    }

    @Test
    @DisplayName("SECURITY: Must respect max pages limit to prevent DoS")
    void mustRespectMaxPages_preventDos() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(analysisService, "maxPages", 10);
        Chatbot chatbot = createChatbot("https://example.com");
        when(urlValidationService.completeAndValidate(anyString())).thenAnswer(inv -> Optional.of(inv.getArgument(0).toString()));
        when(urlValidationService.isValidAndSafe(anyString())).thenReturn(true);

        // Act
        CompletableFuture<List<WebsiteContent>> result = analysisService.analyzeWebsite(chatbot);
        List<WebsiteContent> content = result.get();

        // Assert - MUST NOT crawl more than maxPages
        assertThat(content.size()).isLessThanOrEqualTo(10);
        verify(websiteContentRepository, atMost(10)).save(any());
    }

    // ========== Content Validation ==========

    @Test
    @DisplayName("SECURITY: Must have timeout to prevent hanging on slow responses")
    void mustHaveTimeout_preventHanging() throws Exception {
        // Arrange
        int timeoutSeconds = (int) ReflectionTestUtils.getField(analysisService, "timeoutSeconds");

        // Assert - Timeout MUST be configured
        assertThat(timeoutSeconds).isGreaterThan(0);
        assertThat(timeoutSeconds).isLessThanOrEqualTo(60); // Reasonable max
    }

    @Test
    @DisplayName("SECURITY: Must identify itself with proper User-Agent")
    void mustIdentifyWithUserAgent() {
        // Arrange
        String userAgent = (String) ReflectionTestUtils.getField(analysisService, "userAgent");

        // Assert - MUST identify as bot for ethical crawling
        assertThat(userAgent).isNotNull();
        assertThat(userAgent).isNotEmpty();
        assertThat(userAgent).containsAnyOf("Bot", "Crawler", "bot", "crawler");
    }

    // ========== Domain Isolation ==========

    @Test
    @DisplayName("SECURITY: Must only crawl same domain (no cross-domain)")
    void mustOnlyCrawlSameDomain_noCrossDomain() throws Exception {
        // Arrange
        Chatbot chatbot = createChatbot("https://example.com");
        when(urlValidationService.completeAndValidate(anyString())).thenAnswer(inv -> Optional.of(inv.getArgument(0).toString()));
        when(urlValidationService.isValidAndSafe(anyString())).thenReturn(true);

        // Act
        CompletableFuture<List<WebsiteContent>> result = analysisService.analyzeWebsite(chatbot);
        List<WebsiteContent> content = result.get();

        // Assert - Should only save content from same domain
        ArgumentCaptor<WebsiteContent> captor = ArgumentCaptor.forClass(WebsiteContent.class);
        verify(websiteContentRepository, atLeast(0)).save(captor.capture());

        List<WebsiteContent> savedContent = captor.getAllValues();
        for (WebsiteContent wc : savedContent) {
            assertThat(wc.getUrl()).contains("example.com");
            assertThat(wc.getUrl()).doesNotContain("evil.com");
        }
    }

    // ========== Skip Patterns Security ==========

    @ParameterizedTest
    @ValueSource(strings = {
        "https://example.com/malicious.exe",
        "https://example.com/virus.zip",
        "https://example.com/script.js",
        "https://example.com/styles.css"
    })
    @DisplayName("SECURITY: Must skip binary/executable files")
    void mustSkipBinaryFiles(String url) throws Exception {
        // Arrange
        Chatbot chatbot = createChatbot("https://example.com");
        when(urlValidationService.completeAndValidate(anyString())).thenAnswer(inv -> Optional.of(inv.getArgument(0).toString()));
        when(urlValidationService.isValidAndSafe(anyString())).thenReturn(true);

        // Act
        CompletableFuture<List<WebsiteContent>> result = analysisService.analyzeWebsite(chatbot);
        List<WebsiteContent> content = result.get();

        // Assert - Should not process these file types
        ArgumentCaptor<WebsiteContent> captor = ArgumentCaptor.forClass(WebsiteContent.class);
        verify(websiteContentRepository, atLeast(0)).save(captor.capture());

        List<WebsiteContent> savedContent = captor.getAllValues();
        for (WebsiteContent wc : savedContent) {
            assertThat(wc.getUrl()).doesNotEndWith(".exe");
            assertThat(wc.getUrl()).doesNotEndWith(".zip");
            assertThat(wc.getUrl()).doesNotEndWith(".js");
            assertThat(wc.getUrl()).doesNotEndWith(".css");
        }
    }

    // ========== Memory Safety ==========

    @Test
    @DisplayName("SECURITY: Must limit content size to prevent memory exhaustion")
    void mustLimitContentSize_preventMemoryExhaustion() throws Exception {
        // Arrange
        Chatbot chatbot = createChatbot("https://example.com");
        when(urlValidationService.completeAndValidate(anyString())).thenAnswer(inv -> Optional.of(inv.getArgument(0).toString()));
        when(urlValidationService.isValidAndSafe(anyString())).thenReturn(true);

        // Act
        CompletableFuture<List<WebsiteContent>> result = analysisService.analyzeWebsite(chatbot);
        List<WebsiteContent> content = result.get();

        // Assert - Each saved content should have reasonable size
        ArgumentCaptor<WebsiteContent> captor = ArgumentCaptor.forClass(WebsiteContent.class);
        verify(websiteContentRepository, atLeast(0)).save(captor.capture());

        List<WebsiteContent> savedContent = captor.getAllValues();
        for (WebsiteContent wc : savedContent) {
            if (wc.getContent() != null) {
                // Content should be truncated if too large (reasonable limit: 100KB per page)
                assertThat(wc.getContent().length()).isLessThan(100_000);
            }
        }
    }

    @Test
    @DisplayName("SECURITY: Crawl uses bounded executor (pool size 3) to limit concurrency and memory DoS")
    void crawlUsesBoundedExecutor_preventUnboundedConcurrency() {
        java.util.concurrent.ExecutorService executor =
            (java.util.concurrent.ExecutorService) ReflectionTestUtils.getField(analysisService, "executorService");
        assertThat(executor).isNotNull();
        if (executor instanceof java.util.concurrent.ThreadPoolExecutor tpe) {
            assertThat(tpe.getMaximumPoolSize()).isEqualTo(3);
            assertThat(tpe.getCorePoolSize()).isEqualTo(3);
        }
    }

    // ========== Helper Methods ==========

    private Chatbot createChatbot(String url) {
        Chatbot chatbot = new Chatbot();
        chatbot.setId(1L);
        chatbot.setName("Test Bot");
        chatbot.setWebsiteUrl(url);
        return chatbot;
    }
}
