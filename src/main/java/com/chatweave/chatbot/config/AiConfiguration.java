package com.chatweave.chatbot.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for AI services
 * Uses Claude (Anthropic) for chat and OpenAI for embeddings
 */
@Configuration
public class AiConfiguration {

    /**
     * Primary ChatClient bean using Anthropic Claude
     */
    @Bean
    @Primary
    public ChatClient chatClient(AnthropicChatModel anthropicChatModel) {
        return ChatClient.builder(anthropicChatModel)
                .build();
    }
}
