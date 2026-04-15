package com.prayer_chat.chatbot.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FrontendBaseUrlProvider")
class FrontendBaseUrlProviderTest {

    @Test
    @DisplayName("resolve prefers app.frontend-url when set")
    void resolve_prefersAppFrontendUrl() {
        assertEquals("https://www.prayer-chat.com",
                FrontendBaseUrlProvider.resolve("https://www.prayer-chat.com/", "http://localhost:3000"));
    }

    @Test
    @DisplayName("resolve falls back to CORS list when app URL blank")
    void resolve_fallsBackToCors() {
        assertEquals("https://staging.example.com",
                FrontendBaseUrlProvider.resolve("  ", "http://localhost:3000,https://staging.example.com"));
    }

    @Test
    @DisplayName("resolve ignores invalid app URL and falls back to CORS")
    void resolve_invalidAppUrlFallsBackToCors() {
        assertEquals("https://staging.example.com",
                FrontendBaseUrlProvider.resolve("javascript:alert(1)", "https://staging.example.com"));
    }

    @Test
    @DisplayName("pickFromAllowedOrigins prefers www.prayer-chat.com")
    void pick_prefersWwwPrayerChat() {
        String cors = "http://localhost:3000,https://prayer-chat.com,https://www.prayer-chat.com";
        assertEquals("https://www.prayer-chat.com", FrontendBaseUrlProvider.pickFromAllowedOrigins(cors));
    }

    @Test
    @DisplayName("pickFromAllowedOrigins skips localhost for first public origin")
    void pick_skipsLocalhost() {
        String cors = "http://localhost:3000,https://production.com";
        assertEquals("https://production.com", FrontendBaseUrlProvider.pickFromAllowedOrigins(cors));
    }

    @Test
    @DisplayName("stripTrailingSlash removes single trailing slash")
    void stripTrailingSlash() {
        assertEquals("https://x.com", FrontendBaseUrlProvider.stripTrailingSlash("https://x.com/"));
    }

    @Test
    @DisplayName("resolve uses localhost default when nothing else works")
    void resolve_defaultsToLocalhost() {
        assertEquals("http://localhost:3000", FrontendBaseUrlProvider.resolve(null, ""));
        assertEquals("http://localhost:3000", FrontendBaseUrlProvider.resolve("", "   "));
    }

    @Test
    @DisplayName("pickFromAllowedOrigins prefers prayer-chat over vercel")
    void pick_prayerChatOverVercel() {
        String cors = "https://x.vercel.app,https://prayer-chat.com";
        assertTrue(FrontendBaseUrlProvider.pickFromAllowedOrigins(cors).contains("prayer-chat.com"));
    }

    @Test
    @DisplayName("pickFromAllowedOrigins does not treat lookalike host as prayer-chat.com")
    void pick_doesNotMatchLookalikeHost() {
        String cors = "https://evil-prayer-chat.com,https://x.vercel.app";
        assertEquals("https://x.vercel.app", FrontendBaseUrlProvider.pickFromAllowedOrigins(cors));
    }
}
