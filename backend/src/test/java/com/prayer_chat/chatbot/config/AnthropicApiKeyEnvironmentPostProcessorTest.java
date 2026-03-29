package com.prayer_chat.chatbot.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnthropicApiKeyEnvironmentPostProcessor URL normalization tests")
class AnthropicApiKeyEnvironmentPostProcessorTest {

    @Test
    @DisplayName("Converts Render postgres:// URL with userinfo into valid jdbc:postgresql:// with query credentials")
    void convertsPostgresUrlWithUserInfo() {
        String raw = "postgres://user:pass@db.render.com:5432/mydb";
        String jdbc = AnthropicApiKeyEnvironmentPostProcessor.toJdbcUrl(raw);
        assertThat(jdbc).startsWith("jdbc:postgresql://db.render.com:5432/mydb?");
        assertThat(jdbc).contains("user=user");
        assertThat(jdbc).contains("password=pass");
    }

    @Test
    @DisplayName("Keeps existing jdbc: URL unchanged")
    void keepsJdbcUrl() {
        String raw = "jdbc:postgresql://localhost:5432/mydb?sslmode=disable";
        assertThat(AnthropicApiKeyEnvironmentPostProcessor.toJdbcUrl(raw)).isEqualTo(raw);
    }

    @Test
    @DisplayName("Returns null for unsupported schemes")
    void returnsNullForUnsupported() {
        assertThat(AnthropicApiKeyEnvironmentPostProcessor.toJdbcUrl("mysql://x")).isNull();
        assertThat(AnthropicApiKeyEnvironmentPostProcessor.toJdbcUrl("")).isNull();
    }
}

