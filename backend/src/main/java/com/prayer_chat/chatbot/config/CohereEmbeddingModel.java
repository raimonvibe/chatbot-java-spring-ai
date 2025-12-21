package com.prayer_chat.chatbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Custom Cohere implementation of Spring AI's EmbeddingModel using direct HTTP calls
 */
public class CohereEmbeddingModel implements EmbeddingModel {

    private final WebClient webClient;
    private final String model;

    public CohereEmbeddingModel(String apiKey, String model) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.cohere.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.model = model;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        try {
            List<String> texts = request.getInstructions();

            CohereEmbedRequest cohereRequest = new CohereEmbedRequest(
                    texts,
                    model,
                    "search_document"
            );

            CohereEmbedResponse response = webClient.post()
                    .uri("/embed")
                    .bodyValue(cohereRequest)
                    .retrieve()
                    .bodyToMono(CohereEmbedResponse.class)
                    .block();

            List<Embedding> embeddings = response.embeddings.stream()
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
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        try {
            CohereEmbedRequest request = new CohereEmbedRequest(
                    List.of(text),
                    model,
                    "search_document"
            );

            CohereEmbedResponse response = webClient.post()
                    .uri("/embed")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(CohereEmbedResponse.class)
                    .block();

            List<Double> embedding = response.embeddings.get(0);
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
