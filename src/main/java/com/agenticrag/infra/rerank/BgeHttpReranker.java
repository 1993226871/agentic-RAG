package com.agenticrag.infra.rerank;

import com.agenticrag.config.RagProperties;
import com.agenticrag.model.RetrievedDoc;
import com.agenticrag.ports.Reranker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(BgeHttpReranker.class);
    private final RagProperties properties;
    private final RestTemplate restTemplate;
    private final MockBgeReranker fallback = new MockBgeReranker();

    public BgeHttpReranker(RagProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    @Override
    public List<RetrievedDoc> rerank(String query, List<RetrievedDoc> candidates, int topK) {
        long start = System.currentTimeMillis();
        String model = properties.getRerank().getModel();
        String endpoint = properties.getRerank().getEndpoint();
        if (endpoint == null || endpoint.trim().isEmpty()) {
            List<RetrievedDoc> fallbackResult = fallback.rerank(query, candidates, topK);
            log.info("[MODEL][rerank] model={} elapsedMs={} candidates={} topK={} status=fallback_missing_config",
                    model, System.currentTimeMillis() - start, candidates == null ? 0 : candidates.size(), topK);
            return fallbackResult;
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
            body.put("model", model);
            Map<String, Object> input = new HashMap<String, Object>();
            input.put("query", query == null ? "" : query);
            input.put("documents", docs);
            body.put("input", input);
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("top_n", topK);
            body.put("parameters", params);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(endpoint, new HttpEntity<Object>(body, headers), Map.class);
            List<?> resultItems = resolveResults(response);
            if (resultItems == null) {
                List<RetrievedDoc> fallbackResult = fallback.rerank(query, candidates, topK);
                log.info("[MODEL][rerank] model={} elapsedMs={} candidates={} topK={} status=fallback_empty_response",
                        model, System.currentTimeMillis() - start, candidates == null ? 0 : candidates.size(), topK);
                return fallbackResult;
            }
            List<RetrievedDoc> reranked = new ArrayList<RetrievedDoc>();
            for (Object item : resultItems) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<?, ?> row = (Map<?, ?>) item;
                Object indexObj = row.get("index");
                if (!(indexObj instanceof Number)) {
                    continue;
                }
                int index = ((Number) indexObj).intValue();
                if (index < 0 || index >= candidates.size()) {
                    continue;
                }
                Object scoreObj = row.containsKey("relevance_score") ? row.get("relevance_score") : row.get("score");
                double score = scoreObj instanceof Number ? ((Number) scoreObj).doubleValue() : candidates.get(index).score();
                reranked.add(new RetrievedDoc(candidates.get(index).document(), score));
            }
            if (reranked.isEmpty()) {
                List<RetrievedDoc> fallbackResult = fallback.rerank(query, candidates, topK);
                log.info("[MODEL][rerank] model={} elapsedMs={} candidates={} topK={} status=fallback_empty_result",
                        model, System.currentTimeMillis() - start, candidates == null ? 0 : candidates.size(), topK);
                return fallbackResult;
            }
            reranked.sort(Comparator.comparingDouble(RetrievedDoc::score).reversed());
            List<RetrievedDoc> finalResult = reranked.size() > topK ? reranked.subList(0, topK) : reranked;
            log.info("[MODEL][rerank] model={} elapsedMs={} candidates={} topK={} status=success returned={}",
                    model, System.currentTimeMillis() - start, candidates == null ? 0 : candidates.size(), topK, finalResult.size());
            return finalResult;
        } catch (Exception e) {
            List<RetrievedDoc> fallbackResult = fallback.rerank(query, candidates, topK);
            log.warn("[MODEL][rerank] model={} elapsedMs={} candidates={} topK={} status=fallback_exception error={}",
                    model, System.currentTimeMillis() - start, candidates == null ? 0 : candidates.size(), topK, e.getMessage());
            return fallbackResult;
        }
    }

    private List<?> resolveResults(Map<String, Object> response) {
        if (response == null) {
            return null;
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
