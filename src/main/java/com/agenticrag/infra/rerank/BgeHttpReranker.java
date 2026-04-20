package com.agenticrag.infra.rerank;

import com.agenticrag.config.RagProperties;
import com.agenticrag.model.RetrievedDoc;
import com.agenticrag.ports.Reranker;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BgeHttpReranker implements Reranker {
    private final RagProperties properties;
    private final RestTemplate restTemplate;
    private final MockBgeReranker fallback = new MockBgeReranker();

    public BgeHttpReranker(RagProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    @Override
    public List<RetrievedDoc> rerank(String query, List<RetrievedDoc> candidates, int topK) {
        String endpoint = properties.getRerank().getEndpoint();
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return fallback.rerank(query, candidates, topK);
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String apiKey = properties.getRerank().getApiKey();
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                headers.setBearerAuth(apiKey);
            }

            List<String> docs = new ArrayList<String>();
            for (RetrievedDoc candidate : candidates) {
                docs.add(candidate.document().text());
            }
            Map<String, Object> body = new HashMap<String, Object>();
            body.put("model", properties.getRerank().getModel());
            body.put("query", query);
            body.put("documents", docs);
            body.put("top_k", topK);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(endpoint, new HttpEntity<Object>(body, headers), Map.class);
            List<?> resultItems = resolveResults(response);
            if (resultItems == null) {
                return fallback.rerank(query, candidates, topK);
            }
            List<RetrievedDoc> reranked = new ArrayList<RetrievedDoc>();
            for (Object item : resultItems) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<?, ?> row = (Map<?, ?>) item;
                Object indexObj = row.get("index");
                Object scoreObj = row.containsKey("score") ? row.get("score") : row.get("relevance_score");
                if (!(indexObj instanceof Number)) {
                    continue;
                }
                int index = ((Number) indexObj).intValue();
                if (index < 0 || index >= candidates.size()) {
                    continue;
                }
                double score = scoreObj instanceof Number ? ((Number) scoreObj).doubleValue() : candidates.get(index).score();
                reranked.add(new RetrievedDoc(candidates.get(index).document(), score));
            }
            if (reranked.isEmpty()) {
                return fallback.rerank(query, candidates, topK);
            }
            reranked.sort(Comparator.comparingDouble(RetrievedDoc::score).reversed());
            return reranked.size() > topK ? reranked.subList(0, topK) : reranked;
        } catch (Exception e) {
            return fallback.rerank(query, candidates, topK);
        }
    }

    private List<?> resolveResults(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        if (response.get("results") instanceof List) {
            return (List<?>) response.get("results");
        }
        Object outputObj = response.get("output");
        if (!(outputObj instanceof Map)) {
            return null;
        }
        Object resultsObj = ((Map<?, ?>) outputObj).get("results");
        if (resultsObj instanceof List) {
            return (List<?>) resultsObj;
        }
        return null;
    }
}
