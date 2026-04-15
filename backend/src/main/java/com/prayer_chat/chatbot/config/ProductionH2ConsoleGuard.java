package com.prayer_chat.chatbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Fails fast if H2 console is enabled outside local/test profiles.
 */
@Component
public class ProductionH2ConsoleGuard {

    private final Environment environment;
    private final boolean h2ConsoleEnabled;

    public ProductionH2ConsoleGuard(
            Environment environment,
            @Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled) {
        this.environment = environment;
        this.h2ConsoleEnabled = h2ConsoleEnabled;
    }

    @PostConstruct
    void validate() {
        if (!h2ConsoleEnabled) {
            return;
        }
        boolean localLike = environment.acceptsProfiles(Profiles.of("local", "test"));
        if (!localLike) {
            throw new IllegalStateException(
                    "spring.h2.console.enabled=true is not allowed outside local/test profiles.");
        }
    }
}
