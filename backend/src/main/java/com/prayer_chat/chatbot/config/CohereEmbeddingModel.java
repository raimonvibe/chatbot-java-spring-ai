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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Custom Cohere implementation of Spring AI's EmbeddingModel using Java's native HttpClient
 * Uses Java 11+ HttpClient instead of WebClient/Reactor Netty to avoid QUIC library issues
 */
public class CohereEmbeddingModel implements EmbeddingModel {

    /** Cohere embed-multilingual-v3.0 allows ~512 tokens per text; keep payload small for latency. */
    static final int DEFAULT_MAX_TEXT_CHARS = 2048;
    static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 60;
    static final int MIN_MAX_TEXT_CHARS = 256;
    static final int MAX_MAX_TEXT_CHARS = 4096;
    static final int MIN_REQUEST_TIMEOUT_SECONDS = 5;
    static final int MAX_REQUEST_TIMEOUT_SECONDS = 120;
    static final int MAX_BATCH_SIZE = 96;
    static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    static final int MAX_EMBEDDING_DIMENSIONS = 4096;

    private static final URI COHERE_EMBED_URI = URI.create("https://api.cohere.com/v1/embed");
    private static final Pattern SAFE_API_KEY = Pattern.compile("^[\\x21-\\x7E]+$");
    private static final Pattern SAFE_MODEL = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$");

    private static final int MAX_EMBED_ATTEMPTS = 4;
    private static final long INITIAL_BACKOFF_MS = 500L;
    private static final long MAX_BACKOFF_MS = 5_000L;

    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private final int maxTextChars;

    public CohereEmbeddingModel(String apiKey, String model) {
        this(apiKey, model, Duration.ofSeconds(DEFAULT_REQUEST_TIMEOUT_SECONDS), DEFAULT_MAX_TEXT_CHARS);
    }

    public CohereEmbeddingModel(String apiKey, String model, Duration requestTimeout, int maxTextChars) {
        this.apiKey = requireValidApiKey(apiKey);
        this.model = requireValidModel(model);
        long timeoutSeconds = Math.max(MIN_REQUEST_TIMEOUT_SECONDS,
                Math.min(requestTimeout.getSeconds(), MAX_REQUEST_TIMEOUT_SECONDS));
        this.requestTimeout = Duration.ofSeconds(timeoutSeconds);
        this.maxTextChars = Math.max(MIN_MAX_TEXT_CHARS, Math.min(maxTextChars, MAX_MAX_TEXT_CHARS));

        int connectTimeoutSeconds = (int) Math.min(timeoutSeconds, MAX_REQUEST_TIMEOUT_SECONDS);
        // Use Java's native HttpClient (no QUIC, no native libraries). NEVER follow redirects (fixed host).
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(10, connectTimeoutSeconds)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        // ObjectMapper: deserialize only to CohereEmbedResponse (no polymorphic types / gadgets).
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
            List<String> instructions = request.getInstructions();
            if (instructions == null || instructions.isEmpty()) {
                throw new IllegalArgumentException("Embedding request must include at least one text");
            }
            if (instructions.size() > MAX_BATCH_SIZE) {
                throw new IllegalArgumentException("Too many texts in embedding request (max " + MAX_BATCH_SIZE + ")");
            }
            for (String instruction : instructions) {
                if (instruction == null || instruction.trim().isEmpty()) {
                    throw new IllegalArgumentException("Embedding request texts cannot be null or empty");
                }
            }
            List<String> texts = instructions.stream()
                    .map(this::truncateForEmbed)
                    .collect(Collectors.toList());

            CohereEmbedRequest cohereRequest = new CohereEmbedRequest(
                    texts,
                    model,
                    "search_document"
            );

            String requestBody = objectMapper.writeValueAsString(cohereRequest);
            CohereEmbedResponse cohereResponse = parseEmbedResponse(executeEmbedPost(requestBody));

            List<Embedding> embeddings = cohereResponse.embeddings.stream()
                    .map(this::toEmbedding)
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
                    List.of(truncateForEmbed(text)),
                    model,
                    "search_document"
            );

            String requestBody = objectMapper.writeValueAsString(request);
            CohereEmbedResponse cohereResponse = parseEmbedResponse(executeEmbedPost(requestBody));

            if (cohereResponse.embeddings == null || cohereResponse.embeddings.isEmpty()) {
                throw new RuntimeException("Failed to get embedding from Cohere: empty or null embeddings");
            }

            return toEmbedding(cohereResponse.embeddings.get(0)).getOutput();
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
                .uri(COHERE_EMBED_URI)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(requestTimeout)
                .build();
    }

    private String truncateForEmbed(String text) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxTextChars) {
            return text;
        }
        return text.substring(0, maxTextChars);
    }

    private static String requireValidApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Cohere API key is required");
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() > 512 || !SAFE_API_KEY.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid Cohere API key format");
        }
        return trimmed;
    }

    private static String requireValidModel(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Cohere embedding model is required");
        }
        String trimmed = model.trim();
        if (!SAFE_MODEL.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid Cohere embedding model name");
        }
        return trimmed;
    }

    private CohereEmbedResponse parseEmbedResponse(byte[] bodyBytes) throws IOException {
        if (bodyBytes == null || bodyBytes.length == 0) {
            throw new RuntimeException("Failed to get embedding from Cohere: empty response");
        }
        if (bodyBytes.length > MAX_RESPONSE_BYTES) {
            throw new RuntimeException("Cohere response exceeds size limit");
        }
        return objectMapper.readValue(bodyBytes, CohereEmbedResponse.class);
    }

    private Embedding toEmbedding(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            throw new RuntimeException("Failed to get embedding from Cohere: empty vector");
        }
        if (embedding.size() > MAX_EMBEDDING_DIMENSIONS) {
            throw new RuntimeException("Cohere embedding dimension exceeds limit");
        }
        float[] floatArray = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            floatArray[i] = embedding.get(i).floatValue();
        }
        return new Embedding(floatArray, 0);
    }

    /**
     * Retries transient failures (connection reset, timeouts, HTTP 502/503/504, 429, 408).
     * Does not retry 401/403 — fix credentials instead.
     */
    private byte[] executeEmbedPost(String requestBody) {
        long backoff = INITIAL_BACKOFF_MS;
        for (int attempt = 1; attempt <= MAX_EMBED_ATTEMPTS; attempt++) {
            HttpRequest httpRequest = newEmbedRequest(requestBody);
            try {
                HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
                int code = response.statusCode();
                if (code == 200) {
                    byte[] body = response.body();
                    if (body != null && body.length > MAX_RESPONSE_BYTES) {
                        throw new RuntimeException("Cohere response exceeds size limit");
                    }
                    return body != null ? body : new byte[0];
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
