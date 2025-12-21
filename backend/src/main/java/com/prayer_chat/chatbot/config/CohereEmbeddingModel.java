package com.prayer_chat.chatbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;
import org.springframework.lang.NonNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Custom Cohere implementation of Spring AI's EmbeddingModel using Java's native HttpClient
 * Uses Java 11+ HttpClient instead of WebClient/Reactor Netty to avoid QUIC library issues
 */
public class CohereEmbeddingModel implements EmbeddingModel {

    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;

    public CohereEmbeddingModel(String apiKey, String model) {
        // Use Java's native HttpClient (no QUIC, no native libraries)
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @NonNull
    public EmbeddingResponse call(@NonNull EmbeddingRequest request) {
        try {
            List<String> texts = request.getInstructions();

            CohereEmbedRequest cohereRequest = new CohereEmbedRequest(
                    texts,
                    model,
                    "search_document"
            );

            String requestBody = objectMapper.writeValueAsString(cohereRequest);
            
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.cohere.com/v1/embed"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new RuntimeException("Cohere API returned status " + response.statusCode() + ": " + response.body());
            }

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
        } catch (Exception e) {
            throw new RuntimeException("Failed to get embeddings from Cohere", e);
        }
    }

    @Override
    @NonNull
    public float[] embed(@NonNull Document document) {
        return embed(document.getText());
    }

    @Override
    @NonNull
    public float[] embed(@NonNull String text) {
        try {
            CohereEmbedRequest request = new CohereEmbedRequest(
                    List.of(text),
                    model,
                    "search_document"
            );

            String requestBody = objectMapper.writeValueAsString(request);
            
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.cohere.com/v1/embed"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new RuntimeException("Cohere API returned status " + response.statusCode() + ": " + response.body());
            }

            CohereEmbedResponse cohereResponse = objectMapper.readValue(response.body(), CohereEmbedResponse.class);

            List<Double> embedding = cohereResponse.embeddings.get(0);
            float[] floatArray = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                floatArray[i] = embedding.get(i).floatValue();
            }
            return floatArray;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get embedding from Cohere", e);
        }
    }

    @Override
    public int dimensions() {
        return 1024; // embed-multilingual-v3.0 has 1024 dimensions
    }

    // Request/Response classes for Cohere API
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
