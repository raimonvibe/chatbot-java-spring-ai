package com.tjanabot.chatbot.config;

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
        logger.info("🔧 EnvironmentVariableConfig.onApplicationEvent() CALLED!");
        logger.info("🔧 Event type: {}", event.getClass().getName());
        
        ConfigurableEnvironment environment = event.getEnvironment();
        logger.info("🔧 Environment: {}", environment.getClass().getName());
        
        logger.info("🔧 EnvironmentVariableConfig processing environment variables...");
        
        Map<String, Object> properties = new HashMap<>();
        boolean hasProperties = false;
        
        // Read .env file directly (spring-dotenv loads it but not as system env vars)
        Map<String, String> envVars = readEnvFile();
        
        // Process ANTHROPIC_API_KEY -> spring.ai.anthropic.api-key
        logger.info("🔧 Step 1: Reading ANTHROPIC_API_KEY...");
        String anthropicApiKey = System.getenv("ANTHROPIC_API_KEY");
        if ((anthropicApiKey == null || anthropicApiKey.trim().isEmpty()) && envVars != null) {
            anthropicApiKey = envVars.get("ANTHROPIC_API_KEY");
        }
        logger.info("🔧   ANTHROPIC_API_KEY: {}", 
            anthropicApiKey != null && !anthropicApiKey.isEmpty() ? "FOUND (length: " + anthropicApiKey.length() + ")" : "NOT FOUND");
        
        if (anthropicApiKey != null && !anthropicApiKey.trim().isEmpty()) {
            properties.put("spring.ai.anthropic.api-key", anthropicApiKey);
            hasProperties = true;
            logger.info("✅ Set spring.ai.anthropic.api-key from ANTHROPIC_API_KEY (length: {})", anthropicApiKey.length());
        } else {
            logger.warn("⚠️  ANTHROPIC_API_KEY not found. Spring AI auto-configuration may not work.");
        }
        
        // Process JWT_SECRET -> jwt.secret
        logger.info("🔧 Step 2: Reading JWT_SECRET...");
        String jwtSecret = System.getenv("JWT_SECRET");
        if ((jwtSecret == null || jwtSecret.trim().isEmpty()) && envVars != null) {
            jwtSecret = envVars.get("JWT_SECRET");
        }
        logger.info("🔧   JWT_SECRET: {}", 
            jwtSecret != null && !jwtSecret.isEmpty() ? "FOUND (length: " + jwtSecret.length() + ")" : "NOT FOUND");
        
        if (jwtSecret != null && !jwtSecret.trim().isEmpty()) {
            properties.put("jwt.secret", jwtSecret);
            hasProperties = true;
            logger.info("✅ Set jwt.secret from JWT_SECRET (length: {})", jwtSecret.length());
        } else {
            logger.warn("⚠️  JWT_SECRET not found. Application may fail to start.");
        }
        
        // Process COHERE_API_KEY -> spring.ai.cohere.api-key
        logger.info("🔧 Step 3: Reading COHERE_API_KEY...");
        String cohereApiKey = System.getenv("COHERE_API_KEY");
        if ((cohereApiKey == null || cohereApiKey.trim().isEmpty()) && envVars != null) {
            cohereApiKey = envVars.get("COHERE_API_KEY");
        }
        logger.info("🔧   COHERE_API_KEY: {}", 
            cohereApiKey != null && !cohereApiKey.isEmpty() ? "FOUND (length: " + cohereApiKey.length() + ")" : "NOT FOUND");
        if (cohereApiKey != null && !cohereApiKey.trim().isEmpty()) {
            properties.put("spring.ai.cohere.api-key", cohereApiKey);
            hasProperties = true;
            logger.info("✅ Set spring.ai.cohere.api-key from COHERE_API_KEY (length: {})", cohereApiKey.length());
        }
        
        // Process GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET -> OAuth2 properties
        logger.info("🔧 Step 4: Reading GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET...");
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
            logger.info("✅ Set OAuth2 Google credentials (client-id length: {})", googleClientId.length());
        } else {
            // OAuth2 is required - log warning but don't set empty values
            // This will cause startup failure if credentials are missing (as intended)
            logger.error("❌ GOOGLE_CLIENT_ID or GOOGLE_CLIENT_SECRET not found!");
            logger.error("❌ OAuth2 is REQUIRED. Please set both GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET in your .env file.");
            logger.error("❌ Application will fail to start without OAuth2 credentials.");
        }
        
        if (hasProperties) {
            logger.info("🔧 Step 3: Creating MapPropertySource with {} properties", properties.size());
            MapPropertySource propertySource = new MapPropertySource(
                "env-vars-override", properties);
            
            logger.info("🔧 Step 4: Adding property source to environment (highest priority)...");
            logger.info("🔧   Current property sources count: {}", environment.getPropertySources().size());
            
            // Add with highest priority so it overrides any empty values
            environment.getPropertySources().addFirst(propertySource);
            
            logger.info("🔧   New property sources count: {}", environment.getPropertySources().size());
            logger.info("🔧   First property source: {}", environment.getPropertySources().iterator().next().getName());
            
            // Verify the properties were set
            String verifyAnthropic = environment.getProperty("spring.ai.anthropic.api-key");
            String verifyJwt = environment.getProperty("jwt.secret");
            logger.info("🔧 Step 5: Verifying properties after setting...");
            logger.info("🔧   spring.ai.anthropic.api-key: {}", 
                verifyAnthropic != null && !verifyAnthropic.isEmpty() ? "SET (length: " + verifyAnthropic.length() + ")" : "EMPTY/NULL");
            logger.info("🔧   jwt.secret: {}", 
                verifyJwt != null && !verifyJwt.isEmpty() ? "SET (length: " + verifyJwt.length() + ")" : "EMPTY/NULL");
            
            logger.info("✅ EnvironmentVariableConfig completed successfully");
        } else {
            logger.warn("⚠️  No environment variables processed by EnvironmentVariableConfig");
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
                logger.info("🔧 Found .env file at: {}", path.toAbsolutePath());
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
                    logger.info("🔧 Loaded {} variables from .env file", envVars.size());
                    return envVars;
                } catch (IOException e) {
                    logger.warn("⚠️  Failed to read .env file at {}: {}", path, e.getMessage());
                }
            }
        }
        
        logger.info("🔧 No .env file found in common locations");
        return null;
    }
}

