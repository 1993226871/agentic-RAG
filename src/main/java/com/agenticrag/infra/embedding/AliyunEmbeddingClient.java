package com.agenticrag.infra.embedding;

import com.agenticrag.config.RagProperties;
import com.agenticrag.ports.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AliyunEmbeddingClient implements EmbeddingClient {
    private static final Logger log = LoggerFactory.getLogger(AliyunEmbeddingClient.class);
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
        long start = System.currentTimeMillis();
        String model = properties.getEmbedding().getModel();
        int textLen = text == null ? 0 : text.length();
        String apiKey = properties.getEmbedding().getApiKey();
        String endpoint = properties.getEmbedding().getEndpoint();
        if (apiKey == null || apiKey.trim().isEmpty() || endpoint == null || endpoint.trim().isEmpty()) {
            List<Double> fallbackVector = fallback.embed(text);
            log.info("[MODEL][embedding] model={} elapsedMs={} textLen={} status=fallback_missing_config dims={}",
                    model, System.currentTimeMillis() - start, textLen, fallbackVector.size());
            return fallbackVector;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new HashMap<String, Object>();
            body.put("model", model);
            body.put("input", text == null ? "" : text);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(endpoint, new HttpEntity<Object>(body, headers), Map.class);
            if (response == null) {
                List<Double> fallbackVector = fallback.embed(text);
                log.info("[MODEL][embedding] model={} elapsedMs={} textLen={} status=fallback_empty_response dims={}",
                        model, System.currentTimeMillis() - start, textLen, fallbackVector.size());
                return fallbackVector;
            }
            List<Double> vector = extractVector(response);
            if (vector.isEmpty()) {
                List<Double> fallbackVector = fallback.embed(text);
                log.info("[MODEL][embedding] model={} elapsedMs={} textLen={} status=fallback_empty_vector dims={}",
                        model, System.currentTimeMillis() - start, textLen, fallbackVector.size());
                return fallbackVector;
            }
            log.info("[MODEL][embedding] model={} elapsedMs={} textLen={} status=success dims={}",
                    model, System.currentTimeMillis() - start, textLen, vector.size());
            return vector;
        } catch (Exception e) {
            List<Double> fallbackVector = fallback.embed(text);
            log.warn("[MODEL][embedding] model={} elapsedMs={} textLen={} status=fallback_exception error={}",
                    model, System.currentTimeMillis() - start, textLen, e.getMessage());
            return fallbackVector;
        }
    }

    private List<Double> extractVector(Map<String, Object> response) {
        Object dataObj = response.get("data");
        if (!(dataObj instanceof List) || ((List<?>) dataObj).isEmpty()) {
            return new ArrayList<Double>();
        }
        Object first = ((List<?>) dataObj).get(0);
        if (!(first instanceof Map)) {
            return new ArrayList<Double>();
        }
        Object embeddingObj = ((Map<?, ?>) first).get("embedding");
        if (!(embeddingObj instanceof List)) {
            return new ArrayList<Double>();
        }
        List<?> raw = (List<?>) embeddingObj;
        List<Double> vector = new ArrayList<Double>(raw.size());
        for (Object value : raw) {
            if (value instanceof Number) {
                vector.add(((Number) value).doubleValue());
            }
        }
        return vector;
    }
}
