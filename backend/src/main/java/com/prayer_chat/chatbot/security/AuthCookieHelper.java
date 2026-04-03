package com.prayer_chat.chatbot.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/**
 * HttpOnly session cookie for JWT (mitigates theft via XSS reading localStorage).
 * Cross-site dashboard (e.g. Vercel → API on Render) requires SameSite=None; Secure.
 */
public final class AuthCookieHelper {

    public static final String COOKIE_NAME = "PC_AUTH";

    private AuthCookieHelper() {
    }

    /**
     * @param secure typically {@code request.isSecure()} (respects forwarded headers when configured)
     */
    public static void addAuthCookie(HttpServletResponse response, String jwt, long maxAgeSeconds, boolean secure) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(COOKIE_NAME, jwt)
            .httpOnly(true)
            .path("/")
            .maxAge(maxAgeSeconds);
        if (secure) {
            b.secure(true).sameSite("None");
        } else {
            b.secure(false).sameSite("Lax");
        }
        response.addHeader(HttpHeaders.SET_COOKIE, b.build().toString());
    }

    public static void clearAuthCookie(HttpServletResponse response, boolean secure) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(COOKIE_NAME, "")
            .httpOnly(true)
            .path("/")
            .maxAge(0);
        if (secure) {
            b.secure(true).sameSite("None");
        } else {
            b.secure(false).sameSite("Lax");
        }
        response.addHeader(HttpHeaders.SET_COOKIE, b.build().toString());
    }
}
