package com.prayer_chat.chatbot.helpers;

import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches CSRF tokens for WebTestClient / REST Assured tests when {@link com.prayer_chat.chatbot.config.TestSecurityConfig}
 * mirrors production CSRF (cookie + header).
 */
public final class CsrfTestSupport {

    public static final String CSRF_COOKIE = "XSRF-TOKEN";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private CsrfTestSupport() {
    }

    public static String fetchCsrfToken(WebTestClient webTestClient) {
        AtomicReference<String> token = new AtomicReference<>();
        webTestClient.get()
            .uri("/api/health")
            .exchange()
            .expectStatus().isOk()
            .expectCookie()
            .value(CSRF_COOKIE, token::set);
        return token.get();
    }
}
