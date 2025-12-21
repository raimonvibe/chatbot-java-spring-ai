package com.prayer_chat.chatbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CRITICAL SECURITY TESTS for CohereEmbeddingModel
 * Tests security, error handling, and input validation
 *
 * These tests verify:
 * - Null/empty input validation
 * - API key security (not exposed in errors)
 * - Error message security (no sensitive info leakage)
 * - JSON injection prevention
 * - Timeout handling
 * - Invalid response handling
 * - HTTP client security
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CohereEmbeddingModel Security Tests")
class CohereEmbeddingModelTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private CohereEmbeddingModel embeddingModel;
    private final String testApiKey = "test-api-key-12345";
    private final String testModel = "embed-multilingual-v3.0";

    @BeforeEach
    void setUp() throws Exception {
        // Create model with real ObjectMapper but mock HttpClient
        embeddingModel = new CohereEmbeddingModel(testApiKey, testModel);
        
        // Replace HttpClient with mock using reflection
        ReflectionTestUtils.setField(embeddingModel, "httpClient", httpClient);
    }
    
    private void setupSuccessfulResponse() throws Exception {
        // Setup default successful response - only when needed
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(createValidCohereResponse());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
    }

    // ========== SECURITY: Input Validation Tests ==========

    @Test
    @DisplayName("SECURITY: Should reject null text input")
    void shouldRejectNullText() {
        assertThatThrownBy(() -> embeddingModel.embed((String) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("SECURITY: Should reject empty or whitespace-only text")
    void shouldRejectEmptyOrWhitespaceText(String text) {
        assertThatThrownBy(() -> embeddingModel.embed(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    @DisplayName("SECURITY: Should reject null document")
    void shouldRejectNullDocument() {
        assertThatThrownBy(() -> embeddingModel.embed((Document) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("SECURITY: Should reject document with null text")
    void shouldRejectDocumentWithNullText() {
        Document doc = new Document("");
        ReflectionTestUtils.setField(doc, "text", null);
        
        assertThatThrownBy(() -> embeddingModel.embed(doc))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    @DisplayName("SECURITY: Should reject document with empty text")
    void shouldRejectDocumentWithEmptyText() {
        Document doc = new Document("");
        
        assertThatThrownBy(() -> embeddingModel.embed(doc))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    // ========== SECURITY: API Key Protection Tests ==========

    @Test
    @DisplayName("SECURITY: API key should be in Authorization header, not in error messages")
    void shouldNotExposeApiKeyInErrors() throws Exception {
        // Simulate 401 error
        when(httpResponse.statusCode()).thenReturn(401);
        lenient().when(httpResponse.body()).thenReturn("{\"message\":\"Invalid API key\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        assertThatThrownBy(() -> embeddingModel.embed("test text"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Authentication failed")
                .satisfies(e -> {
                    String message = e.getMessage();
                    assertThat(message).doesNotContain(testApiKey);
                    assertThat(message).doesNotContain("api");
                    assertThat(message).doesNotContain("key");
                });
    }

    @Test
    @DisplayName("SECURITY: API key should be in Authorization header")
    void shouldIncludeApiKeyInAuthorizationHeader() throws Exception {
        setupSuccessfulResponse();
        
        embeddingModel.embed("test text");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());

        HttpRequest request = requestCaptor.getValue();
        String authHeader = request.headers().firstValue("Authorization").orElse("");
        
        assertThat(authHeader).isEqualTo("Bearer " + testApiKey);
    }

    // ========== SECURITY: Error Message Security Tests ==========

    @Test
    @DisplayName("SECURITY: Should not expose full response body in 401 error")
    void shouldNotExposeResponseBodyIn401Error() throws Exception {
        when(httpResponse.statusCode()).thenReturn(401);
        lenient().when(httpResponse.body()).thenReturn("{\"error\":\"Invalid API key: sk-xxxxx\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        assertThatThrownBy(() -> embeddingModel.embed("test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Authentication failed")
                .satisfies(e -> {
                    String message = e.getMessage();
                    assertThat(message).doesNotContain("Invalid API key");
                    assertThat(message).doesNotContain("sk-");
                });
    }

    @Test
    @DisplayName("SECURITY: Should not expose full response body in 403 error")
    void shouldNotExposeResponseBodyIn403Error() throws Exception {
        when(httpResponse.statusCode()).thenReturn(403);
        lenient().when(httpResponse.body()).thenReturn("{\"error\":\"Access denied: account suspended\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        assertThatThrownBy(() -> embeddingModel.embed("test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Access forbidden")
                .satisfies(e -> {
                    String message = e.getMessage();
                    assertThat(message).doesNotContain("Access denied");
                    assertThat(message).doesNotContain("suspended");
                });
    }

    @Test
    @DisplayName("SECURITY: Should not expose full response body in 429 error")
    void shouldNotExposeResponseBodyIn429Error() throws Exception {
        when(httpResponse.statusCode()).thenReturn(429);
        lenient().when(httpResponse.body()).thenReturn("{\"error\":\"Rate limit exceeded: 100 requests/hour\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        assertThatThrownBy(() -> embeddingModel.embed("test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Rate limit exceeded")
                .satisfies(e -> {
                    String message = e.getMessage();
                    assertThat(message).doesNotContain("100 requests");
                });
    }

    @Test
    @DisplayName("SECURITY: Should not expose full response body in 500 error")
    void shouldNotExposeResponseBodyIn500Error() throws Exception {
        when(httpResponse.statusCode()).thenReturn(500);
        lenient().when(httpResponse.body()).thenReturn("{\"error\":\"Internal server error: database connection failed\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        assertThatThrownBy(() -> embeddingModel.embed("test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Cohere API error: 500")
                .satisfies(e -> {
                    String message = e.getMessage();
                    assertThat(message).doesNotContain("database");
                    assertThat(message).doesNotContain("connection");
                });
    }

    // ========== SECURITY: JSON Injection Prevention Tests ==========

    @Test
    @DisplayName("SECURITY: Should safely handle malicious JSON in response")
    void shouldSafelyHandleMaliciousJsonResponse() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        // Try to inject malicious JSON with type info
        when(httpResponse.body()).thenReturn(
            "{\"embeddings\":[[0.1,0.2,0.3]],\"@class\":\"com.prayer_chat.chatbot.model.User\"}"
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Should only deserialize to CohereEmbedResponse, ignore @class
        assertThatCode(() -> embeddingModel.embed("test"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SECURITY: Should safely handle JSON with unknown properties")
    void shouldSafelyHandleJsonWithUnknownProperties() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(
            "{\"embeddings\":[[0.1,0.2,0.3]],\"malicious_field\":\"value\",\"@type\":\"SomeClass\"}"
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Should ignore unknown properties and not crash
        assertThatCode(() -> embeddingModel.embed("test"))
                .doesNotThrowAnyException();
    }

    // ========== SECURITY: Timeout and Network Error Tests ==========

    @Test
    @DisplayName("SECURITY: Should handle timeout gracefully")
    void shouldHandleTimeoutGracefully() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new java.net.http.HttpTimeoutException("Request timed out"));

        assertThatThrownBy(() -> embeddingModel.embed("test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to get embedding");
    }

    @Test
    @DisplayName("SECURITY: Should handle network errors gracefully")
    void shouldHandleNetworkErrorsGracefully() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new IOException("Connection refused"));

        assertThatThrownBy(() -> embeddingModel.embed("test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to get embedding");
    }

    // ========== Functional Tests ==========

    @Test
    @DisplayName("Should successfully embed single text")
    void shouldEmbedSingleText() throws Exception {
        setupSuccessfulResponse();
        
        float[] result = embeddingModel.embed("test text");

        assertThat(result).isNotNull();
        assertThat(result.length).isEqualTo(3); // Based on mock response
        assertThat(result[0]).isEqualTo(0.1f);
        assertThat(result[1]).isEqualTo(0.2f);
        assertThat(result[2]).isEqualTo(0.3f);
    }

    @Test
    @DisplayName("Should successfully embed document")
    void shouldEmbedDocument() throws Exception {
        setupSuccessfulResponse();
        
        Document doc = new Document("test document text");
        float[] result = embeddingModel.embed(doc);

        assertThat(result).isNotNull();
        assertThat(result.length).isEqualTo(3);
    }

    @Test
    @DisplayName("Should successfully embed multiple texts")
    void shouldEmbedMultipleTexts() throws Exception {
        // EmbeddingRequest requires EmbeddingOptions - use null for default
        EmbeddingRequest request = new EmbeddingRequest(List.of("text1", "text2", "text3"), null);
        
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(createValidCohereResponseMultiple());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        EmbeddingResponse response = embeddingModel.call(request);

        assertThat(response).isNotNull();
        assertThat(response.getResult().getOutput()).hasSize(3);
    }

    @Test
    @DisplayName("Should return correct dimensions")
    void shouldReturnCorrectDimensions() {
        int dimensions = embeddingModel.dimensions();
        assertThat(dimensions).isEqualTo(1024);
    }

    @Test
    @DisplayName("Should use correct model in request")
    void shouldUseCorrectModelInRequest() throws Exception {
        setupSuccessfulResponse();
        
        embeddingModel.embed("test");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());

        HttpRequest request = requestCaptor.getValue();
        // Verify URI is correct
        assertThat(request.uri()).isEqualTo(URI.create("https://api.cohere.com/v1/embed"));
        
        // Verify Content-Type header
        String contentType = request.headers().firstValue("Content-Type").orElse("");
        assertThat(contentType).isEqualTo("application/json");
    }

    @Test
    @DisplayName("Should handle empty embeddings list in response")
    void shouldHandleEmptyEmbeddingsList() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"embeddings\":[]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        assertThatThrownBy(() -> embeddingModel.embed("test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to get embedding");
    }

    @Test
    @DisplayName("Should handle invalid JSON response")
    void shouldHandleInvalidJsonResponse() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("invalid json");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        assertThatThrownBy(() -> embeddingModel.embed("test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to get embedding");
    }

    @Test
    @DisplayName("Should handle null embeddings in response")
    void shouldHandleNullEmbeddingsInResponse() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"embeddings\":null}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Null embeddings will cause IndexOutOfBoundsException when trying to get(0)
        // This is caught and wrapped in RuntimeException
        assertThatThrownBy(() -> embeddingModel.embed("test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to get embedding");
    }

    // ========== Helper Methods ==========

    private String createValidCohereResponse() {
        return "{\"embeddings\":[[0.1,0.2,0.3]]}";
    }

    private String createValidCohereResponseMultiple() {
        return "{\"embeddings\":[[0.1,0.2,0.3],[0.4,0.5,0.6],[0.7,0.8,0.9]]}";
    }
}

