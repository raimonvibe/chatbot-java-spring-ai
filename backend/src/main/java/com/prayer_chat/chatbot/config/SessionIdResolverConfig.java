package com.prayer_chat.chatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Harden session id handling against malformed cookie values.
 *
 * Postgres rejects NUL bytes (0x00) in text parameters; if an attacker (or buggy client)
 * sends a session cookie containing %00, URL-decoding can yield a NUL byte and cause a 500
 * during Spring Session JDBC lookups. We treat such session ids as invalid and ignore them.
 */
@Configuration
public class SessionIdResolverConfig {

    private static final Logger logger = LoggerFactory.getLogger(SessionIdResolverConfig.class);

    // Spring Session ids are typically URL-safe Base64 / hex-ish. Keep this intentionally strict.
    private static final Pattern SAFE_SESSION_ID = Pattern.compile("^[A-Za-z0-9\\-_=]{8,200}$");

    @Bean
    public HttpSessionIdResolver httpSessionIdResolver() {
        // Delegate to the framework default Cookie serializer behavior so we do not
        // accidentally weaken cookie attributes (Secure/SameSite/Path/Domain).
        CookieHttpSessionIdResolver delegate = new CookieHttpSessionIdResolver();

        return new HttpSessionIdResolver() {
            @Override
            public List<String> resolveSessionIds(jakarta.servlet.http.HttpServletRequest request) {
                List<String> ids = delegate.resolveSessionIds(request);
                if (ids == null || ids.isEmpty()) return List.of();

                // Only accept the first valid id; ignore the rest.
                for (String id : ids) {
                    if (id == null || id.isBlank()) continue;
                    if (id.indexOf('\u0000') >= 0) {
                        logger.warn("Rejecting session id containing NUL byte");
                        continue;
                    }
                    if (!SAFE_SESSION_ID.matcher(id).matches()) {
                        // Avoid logging the id itself.
                        logger.warn("Rejecting session id with unexpected format");
                        continue;
                    }
                    return List.of(id);
                }
                return List.of();
            }

            @Override
            public void setSessionId(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, String sessionId) {
                delegate.setSessionId(request, response, sessionId);
            }

            @Override
            public void expireSession(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
                delegate.expireSession(request, response);
            }
        };
    }
}

