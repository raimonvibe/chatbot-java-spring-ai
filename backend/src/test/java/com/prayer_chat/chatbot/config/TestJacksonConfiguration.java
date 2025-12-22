package com.prayer_chat.chatbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Test configuration for Jackson ObjectMapper
 * Automatically loaded in test profile
 * 
 * Updated for Jackson 2.x compatibility:
 * - Uses com.fasterxml.jackson (Jackson 2.x)
 * - Spring Boot 4.0 uses Jackson 3.x by default, but we override to Jackson 2.x
 * - Provides @Primary ObjectMapper for tests (AiConfiguration is not active in test profile)
 */
@Configuration
@Profile("test")
public class TestJacksonConfiguration {

    /**
     * Provides a Jackson 2.x ObjectMapper for tests.
     * This is needed because AiConfiguration is not active in test profile,
     * and Spring Boot 4.0's default ObjectMapper might be Jackson 3.x.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
