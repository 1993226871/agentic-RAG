package com.agenticrag.infra.embedding;

import com.agenticrag.config.RagProperties;
import com.agenticrag.ports.EmbeddingClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AliyunEmbeddingClient implements EmbeddingClient {
    private final RagProperties properties;
    private final RestTemplate restTemplate;
    private final MockAliEmbeddingClient fallback;

    public AliyunEmbeddingClient(RagProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.fallback = new MockAliEmbeddingClient(properties.getEs().getVectorDims());
    }

    @Override
    public List<Double> embed(String text) {
        String apiKey = properties.getEmbedding().getApiKey();
        String endpoint = properties.getEmbedding().getEndpoint();
        if (apiKey == null || apiKey.trim().isEmpty() || endpoint == null || endpoint.trim().isEmpty()) {
            return fallback.embed(text);
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new HashMap<String, Object>();
            body.put("model", properties.getEmbedding().getModel());
            Map<String, Object> input = new HashMap<String, Object>();
            input.put("texts", java.util.Collections.singletonList(text));
            body.put("input", input);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(endpoint, new HttpEntity<Object>(body, headers), Map.class);
            if (response == null) {
                return fallback.embed(text);
            }
            List<Double> vector = extractVector(response);
            if (vector.isEmpty()) {
                return fallback.embed(text);
            }
            return vector;
        } catch (Exception e) {
            return fallback.embed(text);
        }
    }

    private List<Double> extractVector(Map<String, Object> response) {
        // DashScope text embedding format: output.embeddings[0].embedding
        Object outputObj = response.get("output");
        if (outputObj instanceof Map) {
            Object embeddingsObj = ((Map<?, ?>) outputObj).get("embeddings");
            List<Double> parsed = parseEmbeddingArray(embeddingsObj);
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }
        // OpenAI-compatible format: data[0].embedding
        Object dataObj = response.get("data");
        if (dataObj instanceof List && !((List<?>) dataObj).isEmpty()) {
            Object first = ((List<?>) dataObj).get(0);
            if (first instanceof Map) {
                Object embeddingObj = ((Map<?, ?>) first).get("embedding");
                return parseEmbeddingValues(embeddingObj);
            }
        }
        return new ArrayList<Double>();
    }

    private List<Double> parseEmbeddingArray(Object embeddingsObj) {
        if (!(embeddingsObj instanceof List) || ((List<?>) embeddingsObj).isEmpty()) {
            return new ArrayList<Double>();
        }
        Object first = ((List<?>) embeddingsObj).get(0);
        if (!(first instanceof Map)) {
            return new ArrayList<Double>();
        }
        Object vectorObj = ((Map<?, ?>) first).get("embedding");
        return parseEmbeddingValues(vectorObj);
    }

    private List<Double> parseEmbeddingValues(Object vectorObj) {
        if (!(vectorObj instanceof List)) {
            return new ArrayList<Double>();
        }
        List<?> raw = (List<?>) vectorObj;
        List<Double> vector = new ArrayList<Double>(raw.size());
        for (Object value : raw) {
            if (value instanceof Number) {
                vector.add(((Number) value).doubleValue());
            }
        }
        return vector;
    }
}
