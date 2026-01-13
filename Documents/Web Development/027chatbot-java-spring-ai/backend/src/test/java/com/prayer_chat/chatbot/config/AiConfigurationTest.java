package com.prayer_chat.chatbot.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * CRITICAL SECURITY TESTS for AiConfiguration
 * 
 * These tests verify:
 * - API key validation and error handling
 * - Fallback mechanism when Spring AI auto-configuration fails
 * - Security: No API keys exposed in error messages
 * - Proper ChatModel creation with manual fallback
 * - ChatClient and EmbeddingModel bean creation
 * - Error handling for invalid configurations
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiConfiguration Security Tests")
class AiConfigurationTest {

    private AiConfiguration aiConfiguration;

    @BeforeEach
    void setUp() {
        // Create configuration instance
        aiConfiguration = new AiConfiguration();
    }

    // ========== SECURITY: API Key Validation Tests ==========

    @Test
    @DisplayName("SECURITY: Should throw error when ANTHROPIC_API_KEY is not set")
    void shouldThrowErrorWhenAnthropicApiKeyNotSet() {
        // Arrange - Clear API key (both field and simulate empty env var)
        ReflectionTestUtils.setField(aiConfiguration, "anthropicApiKey", "");
        
        // Mock System.getenv by temporarily clearing the env var
        // Since we can't mock System.getenv, we test with empty field which should trigger the same path
        ChatModel chatModel = invokeChatModel();
        
        // Act & Assert - Should throw IllegalStateException when used
        Prompt testPrompt = new Prompt("test");
        assertThatThrownBy(() -> chatModel.call(testPrompt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ChatModel not properly configured")
                .hasMessageContaining("ANTHROPIC_API_KEY")
                .satisfies(e -> {
                    String message = e.getMessage();
                    // SECURITY: Should not expose any API key values
                    assertThat(message).doesNotContain("sk-");
                });
    }

    @Test
    @DisplayName("SECURITY: Should throw error when ANTHROPIC_API_KEY is empty string")
    void shouldThrowErrorWhenAnthropicApiKeyIsEmpty() {
        // Arrange - Set to whitespace only
        ReflectionTestUtils.setField(aiConfiguration, "anthropicApiKey", "   ");
        
        ChatModel chatModel = invokeChatModel();
        
        // Act & Assert
        Prompt testPrompt = new Prompt("test");
        assertThatThrownBy(() -> chatModel.call(testPrompt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ChatModel not properly configured");
    }

    @Test
    @DisplayName("SECURITY: Should not expose API key in error messages")
    void shouldNotExposeApiKeyInErrorMessages() {
        // Arrange - Set a test API key
        String testApiKey = "sk-test-key-12345-secret";
        ReflectionTestUtils.setField(aiConfiguration, "anthropicApiKey", testApiKey);
        
        // Mock AnthropicApi.builder() to throw exception (simulating failure)
        try (var anthropicApiMock = mockStatic(AnthropicApi.class)) {
            AnthropicApi.Builder mockBuilder = mock(AnthropicApi.Builder.class);
            anthropicApiMock.when(AnthropicApi::builder).thenReturn(mockBuilder);
            when(mockBuilder.apiKey(anyString())).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenThrow(new RuntimeException("API connection failed"));
            
            ChatModel chatModel = invokeChatModel();
            
            // Act & Assert
            Prompt testPrompt = new Prompt("test");
            assertThatThrownBy(() -> chatModel.call(testPrompt))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ChatModel configuration error")
                    .satisfies(e -> {
                        String message = e.getMessage();
                        // SECURITY: Should not expose API key
                        assertThat(message).doesNotContain(testApiKey);
                        assertThat(message).doesNotContain("sk-test");
                        assertThat(message).doesNotContain("12345");
                    });
        }
    }

    // ========== Functional: Manual ChatModel Creation Tests ==========

    @Test
    @DisplayName("Should create AnthropicChatModel manually when API key is set")
    void shouldCreateAnthropicChatModelManually() {
        // Arrange - Set API key
        String testApiKey = "sk-test-key-valid";
        ReflectionTestUtils.setField(aiConfiguration, "anthropicApiKey", testApiKey);
        
        // Mock the builder chain
        try (var anthropicApiMock = mockStatic(AnthropicApi.class);
             var optionsMock = mockStatic(AnthropicChatOptions.class);
             var modelMock = mockStatic(AnthropicChatModel.class)) {
            
            // Mock AnthropicApi
            AnthropicApi.Builder apiBuilder = mock(AnthropicApi.Builder.class);
            AnthropicApi mockApi = mock(AnthropicApi.class);
            anthropicApiMock.when(AnthropicApi::builder).thenReturn(apiBuilder);
            when(apiBuilder.apiKey(testApiKey)).thenReturn(apiBuilder);
            when(apiBuilder.build()).thenReturn(mockApi);
            
            // Mock AnthropicChatOptions
            AnthropicChatOptions.Builder optionsBuilder = mock(AnthropicChatOptions.Builder.class);
            AnthropicChatOptions mockOptions = mock(AnthropicChatOptions.class);
            optionsMock.when(AnthropicChatOptions::builder).thenReturn(optionsBuilder);
            when(optionsBuilder.model(anyString())).thenReturn(optionsBuilder);
            when(optionsBuilder.temperature(anyDouble())).thenReturn(optionsBuilder);
            when(optionsBuilder.maxTokens(anyInt())).thenReturn(optionsBuilder);
            when(optionsBuilder.build()).thenReturn(mockOptions);
            
            // Mock AnthropicChatModel
            AnthropicChatModel.Builder modelBuilder = mock(AnthropicChatModel.Builder.class);
            AnthropicChatModel mockModel = mock(AnthropicChatModel.class);
            modelMock.when(AnthropicChatModel::builder).thenReturn(modelBuilder);
            when(modelBuilder.anthropicApi(mockApi)).thenReturn(modelBuilder);
            when(modelBuilder.defaultOptions(mockOptions)).thenReturn(modelBuilder);
            when(modelBuilder.build()).thenReturn(mockModel);
            
            // Act
            ChatModel chatModel = invokeChatModel();
            
            // Assert
            assertThat(chatModel).isNotNull();
            assertThat(chatModel).isInstanceOf(AnthropicChatModel.class);
            
            // Verify builder was called with correct API key
            verify(apiBuilder).apiKey(testApiKey);
            verify(apiBuilder).build();
            verify(modelBuilder).anthropicApi(mockApi);
            verify(modelBuilder).defaultOptions(mockOptions);
            verify(modelBuilder).build();
        }
    }

    @Test
    @DisplayName("Should use field value when property is set")
    void shouldUseFieldValueWhenPropertyIsSet() {
        // Arrange - Property is set
        String testApiKey = "sk-field-key-123";
        ReflectionTestUtils.setField(aiConfiguration, "anthropicApiKey", testApiKey);
        
        // Mock the builder chain
        try (var anthropicApiMock = mockStatic(AnthropicApi.class);
             var optionsMock = mockStatic(AnthropicChatOptions.class);
             var modelMock = mockStatic(AnthropicChatModel.class)) {
            
            AnthropicApi.Builder apiBuilder = mock(AnthropicApi.Builder.class);
            AnthropicApi mockApi = mock(AnthropicApi.class);
            anthropicApiMock.when(AnthropicApi::builder).thenReturn(apiBuilder);
            when(apiBuilder.apiKey(testApiKey)).thenReturn(apiBuilder);
            when(apiBuilder.build()).thenReturn(mockApi);
            
            AnthropicChatOptions.Builder optionsBuilder = mock(AnthropicChatOptions.Builder.class);
            AnthropicChatOptions mockOptions = mock(AnthropicChatOptions.class);
            optionsMock.when(AnthropicChatOptions::builder).thenReturn(optionsBuilder);
            when(optionsBuilder.model(anyString())).thenReturn(optionsBuilder);
            when(optionsBuilder.temperature(anyDouble())).thenReturn(optionsBuilder);
            when(optionsBuilder.maxTokens(anyInt())).thenReturn(optionsBuilder);
            when(optionsBuilder.build()).thenReturn(mockOptions);
            
            AnthropicChatModel.Builder modelBuilder = mock(AnthropicChatModel.Builder.class);
            AnthropicChatModel mockModel = mock(AnthropicChatModel.class);
            modelMock.when(AnthropicChatModel::builder).thenReturn(modelBuilder);
            when(modelBuilder.anthropicApi(mockApi)).thenReturn(modelBuilder);
            when(modelBuilder.defaultOptions(mockOptions)).thenReturn(modelBuilder);
            when(modelBuilder.build()).thenReturn(mockModel);
            
            // Act
            ChatModel chatModel = invokeChatModel();
            
            // Assert
            assertThat(chatModel).isNotNull();
            verify(apiBuilder).apiKey(testApiKey);
        }
    }

    // ========== Functional: Bean Creation Tests ==========

    @Test
    @DisplayName("Should create ChatClient bean with ChatModel")
    void shouldCreateChatClientBean() {
        // Arrange
        ChatModel mockChatModel = mock(ChatModel.class);
        
        // Act
        ChatClient chatClient = aiConfiguration.chatClient(mockChatModel);
        
        // Assert
        assertThat(chatClient).isNotNull();
    }

    @Test
    @DisplayName("Should create EmbeddingModel bean with Cohere API key")
    void shouldCreateEmbeddingModelBean() {
        // Arrange
        String testCohereKey = "cohere-test-key";
        String testModel = "embed-multilingual-v3.0";
        ReflectionTestUtils.setField(aiConfiguration, "cohereApiKey", testCohereKey);
        ReflectionTestUtils.setField(aiConfiguration, "embeddingModel", testModel);
        
        // Act
        var embeddingModel = aiConfiguration.embeddingModel();
        
        // Assert
        assertThat(embeddingModel).isNotNull();
        assertThat(embeddingModel).isInstanceOf(CohereEmbeddingModel.class);
    }

    @Test
    @DisplayName("Should create VectorStore bean with JdbcTemplate and EmbeddingModel")
    void shouldCreateVectorStoreBean() {
        // Arrange
        var mockJdbcTemplate = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var mockEmbeddingModel = mock(org.springframework.ai.embedding.EmbeddingModel.class);

        // Act
        var vectorStore = aiConfiguration.vectorStore(mockJdbcTemplate, mockEmbeddingModel);

        // Assert
        assertThat(vectorStore).isNotNull();
    }

    // ========== Error Handling Tests ==========

    @Test
    @DisplayName("Should handle exception during AnthropicChatModel creation gracefully")
    void shouldHandleExceptionDuringModelCreation() {
        // Arrange
        String testApiKey = "sk-test-key";
        ReflectionTestUtils.setField(aiConfiguration, "anthropicApiKey", testApiKey);
        
        // Mock AnthropicApi.builder() to throw exception
        try (var anthropicApiMock = mockStatic(AnthropicApi.class)) {
            anthropicApiMock.when(AnthropicApi::builder)
                    .thenThrow(new RuntimeException("Dependency missing"));
            
            ChatModel chatModel = invokeChatModel();
            
            // Act & Assert
            Prompt testPrompt = new Prompt("test");
            assertThatThrownBy(() -> chatModel.call(testPrompt))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ChatModel configuration error")
                    .hasMessageContaining("failed to create AnthropicChatModel")
                    .satisfies(e -> {
                        String message = e.getMessage();
                        // Should not expose API key
                        assertThat(message).doesNotContain(testApiKey);
                    });
        }
    }

    // ========== Helper Methods ==========

    /**
     * Helper method to invoke the private chatModel() method using reflection
     */
    private ChatModel invokeChatModel() {
        try {
            Method chatModelMethod = AiConfiguration.class.getDeclaredMethod("chatModel");
            chatModelMethod.setAccessible(true);
            return (ChatModel) chatModelMethod.invoke(aiConfiguration);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke chatModel() method", e);
        }
    }
}

