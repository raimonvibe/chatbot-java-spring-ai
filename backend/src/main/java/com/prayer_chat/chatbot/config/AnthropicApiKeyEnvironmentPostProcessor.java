package com.prayer_chat.chatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * LONG-TERM SOLUTION: EnvironmentPostProcessor to ensure ANTHROPIC_API_KEY is available
 * as spring.ai.anthropic.api-key property before Spring AI auto-configuration runs.
 * 
 * This solves the timing issue where:
 * - spring-dotenv loads .env files
 * - But Spring AI auto-configuration runs before property resolution completes
 * 
 * Solution: This processor runs early and sets the property directly from the environment variable.
 * 
 * Spring Boot 4+: implement {@link org.springframework.boot.EnvironmentPostProcessor} (moved from {@code org.springframework.boot.env}).
 */
public class AnthropicApiKeyEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AnthropicApiKeyEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        logger.debug("AnthropicApiKeyEnvironmentPostProcessor running");
        
        Map<String, Object> properties = new HashMap<>();
        boolean hasProperties = false;
        
        // Process ANTHROPIC_API_KEY -> spring.ai.anthropic.api-key
        String anthropicApiKey = environment.getProperty("ANTHROPIC_API_KEY");
        if (anthropicApiKey == null || anthropicApiKey.trim().isEmpty()) {
            anthropicApiKey = System.getenv("ANTHROPIC_API_KEY");
        }
        if (anthropicApiKey != null && !anthropicApiKey.trim().isEmpty()) {
            properties.put("spring.ai.anthropic.api-key", anthropicApiKey);
            hasProperties = true;
            logger.info("Configured spring.ai.anthropic.api-key from ANTHROPIC_API_KEY");
        } else {
            logger.warn("ANTHROPIC_API_KEY not set; Spring AI auto-configuration may not create ChatModel");
        }
        
        // Process JWT_SECRET -> jwt.secret
        String jwtSecret = environment.getProperty("JWT_SECRET");
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            jwtSecret = System.getenv("JWT_SECRET");
        }
        if (jwtSecret != null && !jwtSecret.trim().isEmpty()) {
            properties.put("jwt.secret", jwtSecret);
            hasProperties = true;
            logger.info("Configured jwt.secret from JWT_SECRET");
        } else {
            logger.warn("JWT_SECRET not set; application may fail to start");
        }
        
        // Process COHERE_API_KEY -> spring.ai.cohere.api-key
        String cohereApiKey = environment.getProperty("COHERE_API_KEY");
        if (cohereApiKey == null || cohereApiKey.trim().isEmpty()) {
            cohereApiKey = System.getenv("COHERE_API_KEY");
        }
        if (cohereApiKey != null && !cohereApiKey.trim().isEmpty()) {
            properties.put("spring.ai.cohere.api-key", cohereApiKey);
            hasProperties = true;
            logger.info("Configured spring.ai.cohere.api-key from COHERE_API_KEY");
        }

        // Normalize DATABASE_URL for Spring Session JDBC: Render (and others) often give postgresql:// or postgres://
        // without "jdbc:" prefix, which causes "Failed to determine DatabaseDriver" in JdbcSessionConfiguration.
        // Security: DATABASE_URL may contain credentials; never log its value.
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            databaseUrl = System.getenv("DATABASE_URL");
        }
        if (databaseUrl != null && !databaseUrl.isBlank()) {
            String jdbcUrl = toJdbcUrl(databaseUrl.trim());
            if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:postgresql://")) {
                // Render and many cloud Postgres require SSL; add sslmode if URL looks like cloud and none set
                boolean looksLikeCloud = jdbcUrl.contains(".render.com") || jdbcUrl.contains(".postgres.render.com");
                if (looksLikeCloud && !jdbcUrl.contains("sslmode=")) {
                    jdbcUrl = jdbcUrl.contains("?") ? jdbcUrl + "&sslmode=require" : jdbcUrl + "?sslmode=require";
                }
                properties.put("spring.datasource.url", jdbcUrl);
                hasProperties = true;
                logger.info("Configured spring.datasource.url from DATABASE_URL (JDBC format)");
            }
        }

        // Normalize Bible boolean env vars: empty string from env (e.g. BIBLE_LOAD_OLD_TESTAMENT=) cannot be
        // converted to boolean and causes "Invalid boolean value []". Always set to "true" or "false".
        String loadOldTestament = getEnvOrProperty(environment, "BIBLE_LOAD_OLD_TESTAMENT");
        properties.put("app.bible.load-old-testament", parseBooleanEnv(loadOldTestament, false));
        hasProperties = true;

        if (hasProperties) {
            MapPropertySource propertySource = new MapPropertySource(
                "env-vars-override", properties);
            // Add with highest priority so it overrides any empty values
            environment.getPropertySources().addFirst(propertySource);
            logger.debug("EnvironmentPostProcessor applied");
        } else {
            logger.warn("No environment variables processed by EnvironmentPostProcessor");
        }
    }

    private static String getEnvOrProperty(ConfigurableEnvironment environment, String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            value = System.getenv(name);
        }
        return value != null ? value.trim() : "";
    }

    /** Parse env value to "true" or "false"; empty/blank or unknown values become default. */
    private static String parseBooleanEnv(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue ? "true" : "false";
        }
        switch (value.toLowerCase()) {
            case "true":
            case "yes":
            case "1":
            case "on":
                return "true";
            default:
                return "false";
        }
    }

    /**
     * Convert platform DATABASE_URL values to a JDBC URL that Spring can use to detect the driver.
     * Supports:
     * - jdbc:postgresql://... (no change)
     * - postgresql://user:pass@host:port/db?x=y
     * - postgres://user:pass@host:port/db?x=y
     *
     * Security: never log the input; it may contain credentials.
     */
    static String toJdbcUrl(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (s.startsWith("jdbc:")) return s;

        String lower = s.toLowerCase();
        if (!lower.startsWith("postgres://") && !lower.startsWith("postgresql://")) {
            return null;
        }

        URI uri;
        try {
            uri = URI.create(s);
        } catch (Exception e) {
            return null;
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) return null;
        int port = uri.getPort(); // -1 if absent
        String path = uri.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) return null;

        StringBuilder jdbc = new StringBuilder();
        jdbc.append("jdbc:postgresql://").append(host);
        if (port > 0) jdbc.append(":").append(port);
        jdbc.append(path);

        // Preserve existing query; add user/password from userInfo if present.
        String query = uri.getQuery();
        String userInfo = uri.getUserInfo();
        String user = null;
        String pass = null;
        if (userInfo != null && !userInfo.isBlank()) {
            int idx = userInfo.indexOf(':');
            if (idx >= 0) {
                user = userInfo.substring(0, idx);
                pass = userInfo.substring(idx + 1);
            } else {
                user = userInfo;
            }
            // decode percent-encoded user/pass if present
            user = user != null ? URLDecoder.decode(user, StandardCharsets.UTF_8) : null;
            pass = pass != null ? URLDecoder.decode(pass, StandardCharsets.UTF_8) : null;
        }

        StringBuilder q = new StringBuilder();
        if (query != null && !query.isBlank()) {
            q.append(query);
        }
        // Only add if not already present in query
        if (user != null && !user.isBlank() && (query == null || !query.contains("user="))) {
            if (q.length() > 0) q.append("&");
            q.append("user=").append(urlEncode(user));
        }
        if (pass != null && !pass.isBlank() && (query == null || !query.contains("password="))) {
            if (q.length() > 0) q.append("&");
            q.append("password=").append(urlEncode(pass));
        }

        if (q.length() > 0) {
            jdbc.append("?").append(q);
        }
        return jdbc.toString();
    }

    private static String urlEncode(String s) {
        // Minimal encoding for query values
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}

