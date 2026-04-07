package com.prayer_chat.chatbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;

import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Custom Cohere implementation of Spring AI's EmbeddingModel using Java's native HttpClient
 * Uses Java 11+ HttpClient instead of WebClient/Reactor Netty to avoid QUIC library issues
 */
public class CohereEmbeddingModel implements EmbeddingModel {

    private static final int MAX_EMBED_ATTEMPTS = 4;
    private static final long INITIAL_BACKOFF_MS = 500L;
    private static final long MAX_BACKOFF_MS = 5_000L;

    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;

    public CohereEmbeddingModel(String apiKey, String model) {
        // Use Java's native HttpClient (no QUIC, no native libraries)
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2)
                .build();
        this.apiKey = apiKey;
        this.model = model;
        // Configure ObjectMapper for security:
        // - Only deserialize to specific classes (CohereEmbedResponse) - prevents gadget chain attacks
        // - Disable features that could allow unsafe deserialization
        // - Default ObjectMapper is safe when only deserializing to known classes
        // Configure ObjectMapper for security:
        // - Only deserialize to specific classes (CohereEmbedResponse) - prevents gadget chain attacks
        // - Disable features that could allow unsafe deserialization
        // - Default ObjectMapper is safe when only deserializing to known classes
        this.objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE)
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
    }

    @Override
    @SuppressWarnings("null") // Interface requires @NonNull but annotation is deprecated in Spring 7.0
    public EmbeddingResponse call(EmbeddingRequest request) {
        try {
            List<String> texts = request.getInstructions();

            CohereEmbedRequest cohereRequest = new CohereEmbedRequest(
                    texts,
                    model,
                    "search_document"
            );

            String requestBody = objectMapper.writeValueAsString(cohereRequest);
            HttpResponse<String> response = executeEmbedPost(requestBody);
            CohereEmbedResponse cohereResponse = objectMapper.readValue(response.body(), CohereEmbedResponse.class);

            List<Embedding> embeddings = cohereResponse.embeddings.stream()
                    .map(embedding -> {
                        float[] floatArray = new float[embedding.size()];
                        for (int i = 0; i < embedding.size(); i++) {
                            floatArray[i] = embedding.get(i).floatValue();
                        }
                        return new Embedding(floatArray, 0);
                    })
                    .collect(Collectors.toList());

            return new EmbeddingResponse(embeddings);
        } catch (RuntimeException e) {
            // Re-throw RuntimeException as-is (preserves our specific error messages)
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get embeddings from Cohere", e);
        }
    }

    @Override
    @SuppressWarnings("null") // Interface requires @NonNull but annotation is deprecated in Spring 7.0
    public float[] embed(Document document) {
        String text = document.getText();
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Document text cannot be null or empty");
        }
        return embed(text);
    }

    @Override
    @SuppressWarnings("null") // Interface requires @NonNull but annotation is deprecated in Spring 7.0
    public float[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }
        try {
            CohereEmbedRequest request = new CohereEmbedRequest(
                    List.of(text),
                    model,
                    "search_document"
            );

            String requestBody = objectMapper.writeValueAsString(request);
            HttpResponse<String> response = executeEmbedPost(requestBody);
            CohereEmbedResponse cohereResponse = objectMapper.readValue(response.body(), CohereEmbedResponse.class);

            if (cohereResponse.embeddings == null || cohereResponse.embeddings.isEmpty()) {
                throw new RuntimeException("Failed to get embedding from Cohere: empty or null embeddings");
            }

            List<Double> embedding = cohereResponse.embeddings.get(0);
            float[] floatArray = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                floatArray[i] = embedding.get(i).floatValue();
            }
            return floatArray;
        } catch (RuntimeException e) {
            // Re-throw RuntimeException as-is (preserves our specific error messages)
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get embedding from Cohere", e);
        }
    }

    @Override
    public int dimensions() {
        return 1024; // embed-multilingual-v3.0 has 1024 dimensions
    }

    private HttpRequest newEmbedRequest(String requestBody) {
        return HttpRequest.newBuilder()
                .uri(URI.create("https://api.cohere.com/v1/embed"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Retries transient failures (connection reset, timeouts, HTTP 502/503/504, 429, 408).
     * Does not retry 401/403 — fix credentials instead.
     */
    private HttpResponse<String> executeEmbedPost(String requestBody) {
        HttpRequest httpRequest = newEmbedRequest(requestBody);
        long backoff = INITIAL_BACKOFF_MS;
        for (int attempt = 1; attempt <= MAX_EMBED_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                int code = response.statusCode();
                if (code == 200) {
                    return response;
                }
                if (isTransientHttpStatus(code) && attempt < MAX_EMBED_ATTEMPTS) {
                    sleepBackoff(backoff);
                    backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
                    continue;
                }
                throw new RuntimeException(cohereErrorMessage(code));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during Cohere request", e);
            } catch (IOException e) {
                if (isTransientNetworkError(e) && attempt < MAX_EMBED_ATTEMPTS) {
                    sleepBackoff(backoff);
                    backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
                    continue;
                }
                throw new RuntimeException("Failed to get embeddings from Cohere", e);
            }
        }
        throw new RuntimeException("Cohere embed failed after " + MAX_EMBED_ATTEMPTS + " attempts");
    }

    private static String cohereErrorMessage(int code) {
        return switch (code) {
            case 401 -> "Authentication failed";
            case 403 -> "Access forbidden";
            case 429 -> "Rate limit exceeded";
            default -> "Cohere API error: " + code;
        };
    }

    private static boolean isTransientHttpStatus(int code) {
        return code == 408 || code == 429 || code == 502 || code == 503 || code == 504;
    }

    private static boolean isTransientNetworkError(IOException e) {
        if (e instanceof HttpTimeoutException || e instanceof HttpConnectTimeoutException) {
            return true;
        }
        if (e instanceof SocketException) {
            return true;
        }
        Throwable cause = e.getCause();
        if (cause instanceof SocketException) {
            return true;
        }
        String m = e.getMessage();
        if (m == null) {
            return false;
        }
        String lower = m.toLowerCase();
        return lower.contains("connection reset")
                || lower.contains("broken pipe")
                || lower.contains("connection refused");
    }

    private static void sleepBackoff(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during Cohere backoff", e);
        }
    }

    // Request/Response classes for Cohere API
    @SuppressWarnings("unused") // Fields are used by Jackson for JSON serialization
    private static class CohereEmbedRequest {
        public List<String> texts;
        public String model;
        @JsonProperty("input_type")
        public String inputType;

        public CohereEmbedRequest(List<String> texts, String model, String inputType) {
            this.texts = texts;
            this.model = model;
            this.inputType = inputType;
        }
    }

    private static class CohereEmbedResponse {
        public List<List<Double>> embeddings;
    }
}
