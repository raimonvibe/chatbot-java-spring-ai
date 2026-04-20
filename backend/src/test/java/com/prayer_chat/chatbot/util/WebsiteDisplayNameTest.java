package com.prayer_chat.chatbot.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("WebsiteDisplayName")
class WebsiteDisplayNameTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "https://payment-solution-guide-for-nigerian.vercel.app/|Payment Solution Guide For Nigerian Chatbot",
        "https://cool-site.netlify.app|Cool Site Chatbot",
        "https://user.github.io/repo-pages|User Chatbot",
        "https://myapp.pages.dev|Myapp Chatbot",
        "https://example.com|Example Chatbot",
        "https://www.shop.example.com|Example Chatbot",
        "https://api.mycompany.io|Mycompany Chatbot",
        "https://www.acme.eu/|Acme Chatbot",
        "https://acme.eu|Acme Chatbot",
        "https://brand.de|Brand Chatbot",
        "https://mysite.io|Mysite Chatbot",
        "|My Chatbot",
        "not-a-url|My Chatbot",
    })
    void suggestedNameFromUrl(String url, String expected) {
        assertEquals(expected, WebsiteDisplayName.suggestedChatbotNameFromUrl(url));
    }

    @Test
    @DisplayName("titleCaseFromSlug handles hyphens and underscores")
    void titleCaseFromSlug() {
        assertEquals("Hello World", WebsiteDisplayName.titleCaseFromSlug("hello-world"));
        assertEquals("A B", WebsiteDisplayName.titleCaseFromSlug("a__b"));
    }

    @Test
    @DisplayName("long slug is truncated to fit chatbot name max length")
    void truncatesLongNames() {
        String slug = "a".repeat(120);
        String out = WebsiteDisplayName.suggestedChatbotNameFromUrl("https://" + slug + ".vercel.app");
        assertEquals(100, out.length());
        assertEquals(" Chatbot", out.substring(out.length() - " Chatbot".length()));
    }
}
