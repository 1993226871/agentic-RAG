package com.agenticrag.infra.rewrite;

import com.agenticrag.config.RagProperties;
import com.agenticrag.ports.QueryRewriter;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QwenHttpQueryRewriter implements QueryRewriter {
    private final RagProperties properties;
    private final RestTemplate restTemplate;
    private final QueryRewriter fallback = new MockQueryRewriter();

    public QwenHttpQueryRewriter(RagProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    @Override
    public List<String> rewrite(String query, int variants) {
        String endpoint = properties.getRewrite().getEndpoint();
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return fallback.rewrite(query, variants);
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (properties.getRewrite().getApiKey() != null && !properties.getRewrite().getApiKey().trim().isEmpty()) {
                headers.setBearerAuth(properties.getRewrite().getApiKey().trim());
            }
            String prompt = buildPrompt(query, variants);
            Map<String, Object> payload = mapOf(
                    "model", properties.getRewrite().getModel(),
                    "prompt", prompt,
                    "variants", variants
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(endpoint, new HttpEntity<Map<String, Object>>(payload, headers), Map.class);
            List<String> parsed = parseRewrites(response);
            return parsed.isEmpty() ? fallback.rewrite(query, variants) : parsed;
        } catch (Exception e) {
            return fallback.rewrite(query, variants);
        }
    }

    private String buildPrompt(String query, int variants) {
        return "你是检索优化助手。请将用户问题改写成 " + variants + " 条不同表达，要求：\n"
                + "1) 保持原意\n"
                + "2) 术语覆盖更全\n"
                + "3) 每行一条，不要解释\n"
                + "用户问题: " + query;
    }

    private List<String> parseRewrites(Map<String, Object> response) {
        List<String> rewrites = new ArrayList<String>();
        if (response == null) {
            return rewrites;
        }
        Object candidates = response.get("rewrites");
        if (candidates instanceof List) {
            List<?> list = (List<?>) candidates;
            for (Object o : list) {
                if (o != null && !String.valueOf(o).trim().isEmpty()) {
                    rewrites.add(String.valueOf(o).trim());
                }
            }
        }
        if (!rewrites.isEmpty()) {
            return rewrites;
        }
        Object text = response.get("text");
        if (text == null) {
            return rewrites;
        }
        String[] lines = String.valueOf(text).split("\\r?\\n");
        for (String line : lines) {
            String cleaned = line.replaceFirst("^\\s*[-\\d\\.\\)]\\s*", "").trim();
            if (!cleaned.isEmpty()) {
                rewrites.add(cleaned);
            }
        }
        return rewrites;
    }

    private Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        java.util.Map<String, Object> out = new java.util.HashMap<String, Object>();
        out.put(k1, v1);
        out.put(k2, v2);
        out.put(k3, v3);
        return out;
    }
}
