package com.chinmayshivratriwar.nl_sql_engine.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Slf4j
public class CohereEmbeddingClient implements EmbeddingModel {

    private static final String COHERE_EMBED_URL = "https://api.cohere.com/v2/embed";
    private static final String MODEL = "embed-english-light-v3.0";

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CohereEmbeddingClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        try {
            List<String> texts = request.getInstructions();

            Map<String, Object> body = Map.of(
                    "texts", texts,
                    "model", MODEL,
                    "input_type", "search_document",
                    "embedding_types", List.of("float")
            );

            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(COHERE_EMBED_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new RuntimeException("Cohere embed failed: " + response.statusCode() + " " + response.body());
            }

            Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);
            Map<String, Object> embeddings = (Map<String, Object>) responseMap.get("embeddings");
            List<List<Double>> floatVectors = (List<List<Double>>) embeddings.get("float");

            List<Embedding> embeddingList = new java.util.ArrayList<>();
            for (int i = 0; i < floatVectors.size(); i++) {
                List<Double> vector = floatVectors.get(i);
                float[] floatArray = new float[vector.size()];
                for (int j = 0; j < vector.size(); j++) {
                    floatArray[j] = vector.get(j).floatValue();
                }
                embeddingList.add(new Embedding(floatArray, i));
            }

            return new EmbeddingResponse(embeddingList);

        } catch (Exception e) {
            log.error("Cohere embedding failed: {}", e.getMessage());
            throw new RuntimeException("Cohere embedding failed", e);
        }
    }

    @Override
    public float[] embed(String text) {
        EmbeddingResponse response = call(
                new EmbeddingRequest(List.of(text), null)
        );
        return response.getResults().get(0).getOutput();
    }

    @Override // Mandated by interface but not used in our flow hence return a dummy embedding
    public float[] embed(Document document) {
        return new float[0];
    }
}