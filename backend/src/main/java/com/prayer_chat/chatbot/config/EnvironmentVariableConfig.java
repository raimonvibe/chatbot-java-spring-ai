package com.prayer_chat.chatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * LONG-TERM SOLUTION: ApplicationListener to ensure environment variables are available
 * as Spring properties before Spring AI auto-configuration runs.
 * 
 * This solves the timing issue where:
 * - spring-dotenv loads .env files
 * - But Spring AI auto-configuration runs before property resolution completes
 * 
 * Solution: This listener runs when the environment is prepared and sets properties
 * directly from environment variables.
 * 
 * This approach works with Spring Boot 4.0.0 (unlike deprecated EnvironmentPostProcessor).
 */
public class EnvironmentVariableConfig implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(EnvironmentVariableConfig.class);

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        logger.debug("EnvironmentVariableConfig processing application event");
        
        ConfigurableEnvironment environment = event.getEnvironment();
        Map<String, Object> properties = new HashMap<>();
        boolean hasProperties = false;
        
        // Read .env file directly (spring-dotenv loads it but not as system env vars)
        Map<String, String> envVars = readEnvFile();
        
        // Process ANTHROPIC_API_KEY -> spring.ai.anthropic.api-key
        String anthropicApiKey = System.getenv("ANTHROPIC_API_KEY");
        if ((anthropicApiKey == null || anthropicApiKey.trim().isEmpty()) && envVars != null) {
            anthropicApiKey = envVars.get("ANTHROPIC_API_KEY");
        }
        if (anthropicApiKey != null && !anthropicApiKey.trim().isEmpty()) {
            properties.put("spring.ai.anthropic.api-key", anthropicApiKey);
            hasProperties = true;
            logger.info("Configured spring.ai.anthropic.api-key from ANTHROPIC_API_KEY");
        } else {
            logger.warn("ANTHROPIC_API_KEY not set; Spring AI auto-configuration may not create ChatModel");
        }
        
        // Process JWT_SECRET -> jwt.secret
        String jwtSecret = System.getenv("JWT_SECRET");
        if ((jwtSecret == null || jwtSecret.trim().isEmpty()) && envVars != null) {
            jwtSecret = envVars.get("JWT_SECRET");
        }
        
        if (jwtSecret != null && !jwtSecret.trim().isEmpty()) {
            properties.put("jwt.secret", jwtSecret);
            hasProperties = true;
            logger.info("Configured jwt.secret from JWT_SECRET");
        } else {
            logger.warn("JWT_SECRET not set; application may fail to start");
        }
        
        // Process COHERE_API_KEY -> spring.ai.cohere.api-key
        String cohereApiKey = System.getenv("COHERE_API_KEY");
        if ((cohereApiKey == null || cohereApiKey.trim().isEmpty()) && envVars != null) {
            cohereApiKey = envVars.get("COHERE_API_KEY");
        }
        if (cohereApiKey != null && !cohereApiKey.trim().isEmpty()) {
            properties.put("spring.ai.cohere.api-key", cohereApiKey);
            hasProperties = true;
            logger.info("Configured spring.ai.cohere.api-key from COHERE_API_KEY");
        }
        
        // Process GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET -> OAuth2 properties
        String googleClientId = System.getenv("GOOGLE_CLIENT_ID");
        if ((googleClientId == null || googleClientId.trim().isEmpty()) && envVars != null) {
            googleClientId = envVars.get("GOOGLE_CLIENT_ID");
        }
        
        String googleClientSecret = System.getenv("GOOGLE_CLIENT_SECRET");
        if ((googleClientSecret == null || googleClientSecret.trim().isEmpty()) && envVars != null) {
            googleClientSecret = envVars.get("GOOGLE_CLIENT_SECRET");
        }
        
        // OAuth2 is REQUIRED - set properties if both are present
        if (googleClientId != null && !googleClientId.trim().isEmpty() &&
            googleClientSecret != null && !googleClientSecret.trim().isEmpty()) {
            properties.put("spring.security.oauth2.client.registration.google.client-id", googleClientId);
            properties.put("spring.security.oauth2.client.registration.google.client-secret", googleClientSecret);
            hasProperties = true;
            logger.info("Configured OAuth2 Google credentials");
        } else {
            logger.error("GOOGLE_CLIENT_ID or GOOGLE_CLIENT_SECRET not set; OAuth2 is required for startup");
        }
        
        if (hasProperties) {
            MapPropertySource propertySource = new MapPropertySource("env-vars-override", properties);
            environment.getPropertySources().addFirst(propertySource);
            logger.debug("EnvironmentVariableConfig applied {} properties", properties.size());
        } else {
            logger.warn("No environment variables processed by EnvironmentVariableConfig");
        }
    }
    
    /**
     * Read .env file directly from common locations
     */
    private Map<String, String> readEnvFile() {
        // Try common .env file locations
        String[] possiblePaths = {
            ".env",                    // Current directory
            "backend/.env",            // Backend directory
            "../.env"                 // Parent directory
        };
        
        for (String pathStr : possiblePaths) {
            Path path = Paths.get(pathStr);
            if (Files.exists(path) && Files.isRegularFile(path)) {
                logger.debug("Found .env file at: {}", path.toAbsolutePath());
                try {
                    Map<String, String> envVars = new HashMap<>();
                    try (Stream<String> lines = Files.lines(path)) {
                        lines.filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("#"))
                             .forEach(line -> {
                                 int equalsIndex = line.indexOf('=');
                                 if (equalsIndex > 0) {
                                     String key = line.substring(0, equalsIndex).trim();
                                     String value = line.substring(equalsIndex + 1).trim();
                                     // Remove quotes if present
                                     if (value.startsWith("\"") && value.endsWith("\"")) {
                                         value = value.substring(1, value.length() - 1);
                                     } else if (value.startsWith("'") && value.endsWith("'")) {
                                         value = value.substring(1, value.length() - 1);
                                     }
                                     envVars.put(key, value);
                                 }
                             });
                    }
                    logger.debug("Loaded {} variables from .env file", envVars.size());
                    return envVars;
                } catch (IOException e) {
                    logger.warn("Failed to read .env file at {}: {}", path, e.getMessage());
                }
            }
        }
        
        logger.debug("No .env file found in common locations");
        return null;
    }
}

