package com.prayer_chat.chatbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 2.x {@link ObjectMapper} for application services (JWT stack and several libraries still on
 * {@code com.fasterxml.jackson.databind}). Spring Boot 4 MVC uses Jackson 3 {@code JsonMapper} for HTTP; this bean is
 * intentionally not {@code @Primary} so it does not override HTTP message conversion.
 */
@Configuration
public class ServiceObjectMapperConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
