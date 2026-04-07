package com.prayer_chat.chatbot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Marker for tests that historically imported a Jackson 2 {@code ObjectMapper} bean.
 * Spring Boot 4 uses Jackson 3 / {@code JsonMapper} for MVC; do not register a @Primary ObjectMapper here.
 */
@Configuration
@Profile("test")
public class TestJacksonConfiguration {
}
