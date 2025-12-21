package com.prayer_chat.chatbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Test configuration for Jackson ObjectMapper
 * Automatically loaded in test profile
 * 
 * Updated for Spring Framework 7+ compatibility:
 * - Replaced deprecated Jackson2ObjectMapperBuilder with direct ObjectMapper instantiation
 * - Spring Boot 4.0 auto-configures ObjectMapper, but we provide a simple default for tests
 */
@Configuration
@Profile("test")
public class TestJacksonConfiguration {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        // Create a simple ObjectMapper for tests
        // Spring Boot 4.0 with Jackson 3 auto-configures this, but we provide a default
        return new ObjectMapper();
    }
}
