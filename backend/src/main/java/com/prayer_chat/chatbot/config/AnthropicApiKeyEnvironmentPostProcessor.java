package com.prayer_chat.chatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
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
 * Note: In Spring Boot 4.0, EnvironmentPostProcessor moved from org.springframework.boot.env
 * to org.springframework.boot package. This has been updated accordingly.
 */
public class AnthropicApiKeyEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AnthropicApiKeyEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        logger.info("🔧 AnthropicApiKeyEnvironmentPostProcessor running...");
        
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
            logger.info("✅ Set spring.ai.anthropic.api-key from ANTHROPIC_API_KEY (length: {})", anthropicApiKey.length());
        } else {
            logger.warn("⚠️  ANTHROPIC_API_KEY not found. Spring AI auto-configuration may not work.");
        }
        
        // Process JWT_SECRET -> jwt.secret
        String jwtSecret = environment.getProperty("JWT_SECRET");
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            jwtSecret = System.getenv("JWT_SECRET");
        }
        if (jwtSecret != null && !jwtSecret.trim().isEmpty()) {
            properties.put("jwt.secret", jwtSecret);
            hasProperties = true;
            logger.info("✅ Set jwt.secret from JWT_SECRET (length: {})", jwtSecret.length());
        } else {
            logger.warn("⚠️  JWT_SECRET not found. Application may fail to start.");
        }
        
        // Process COHERE_API_KEY -> spring.ai.cohere.api-key
        String cohereApiKey = environment.getProperty("COHERE_API_KEY");
        if (cohereApiKey == null || cohereApiKey.trim().isEmpty()) {
            cohereApiKey = System.getenv("COHERE_API_KEY");
        }
        if (cohereApiKey != null && !cohereApiKey.trim().isEmpty()) {
            properties.put("spring.ai.cohere.api-key", cohereApiKey);
            hasProperties = true;
            logger.info("✅ Set spring.ai.cohere.api-key from COHERE_API_KEY (length: {})", cohereApiKey.length());
        }
        
        if (hasProperties) {
            MapPropertySource propertySource = new MapPropertySource(
                "env-vars-override", properties);
            // Add with highest priority so it overrides any empty values
            environment.getPropertySources().addFirst(propertySource);
            logger.info("✅ EnvironmentPostProcessor completed successfully");
        } else {
            logger.warn("⚠️  No environment variables processed by EnvironmentPostProcessor");
        }
    }
}

