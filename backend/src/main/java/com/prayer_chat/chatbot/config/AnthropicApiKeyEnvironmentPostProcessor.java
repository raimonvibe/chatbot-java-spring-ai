package com.prayer_chat.chatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

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
 * Note: In Spring Boot 3.3.5, EnvironmentPostProcessor is in org.springframework.boot.env package.
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
            String jdbcUrl = databaseUrl.trim();
            if (jdbcUrl.startsWith("postgres://")) {
                jdbcUrl = "jdbc:postgresql://" + jdbcUrl.substring("postgres://".length());
            } else if (jdbcUrl.startsWith("postgresql://") && !jdbcUrl.startsWith("jdbc:")) {
                jdbcUrl = "jdbc:" + jdbcUrl;
            }
            if (jdbcUrl.startsWith("jdbc:postgresql://")) {
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
}

