package com.agenticrag.infra.rewrite;

import com.agenticrag.config.RagProperties;
import com.agenticrag.ports.QueryRewriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QwenHttpQueryRewriter implements QueryRewriter {
    private static final Logger log = LoggerFactory.getLogger(QwenHttpQueryRewriter.class);
    private final RagProperties properties;
    private final RestTemplate restTemplate;
    private final QueryRewriter fallback = new MockQueryRewriter();

    public QwenHttpQueryRewriter(RagProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    @Override
    public List<String> rewrite(String query, int variants) {
        long start = System.currentTimeMillis();
        String model = properties.getRewrite().getModel();
        String endpoint = properties.getRewrite().getEndpoint();
        if (endpoint == null || endpoint.trim().isEmpty()) {
            List<String> fallbackResult = fallback.rewrite(query, variants);
            log.info("[MODEL][rewrite] model={} elapsedMs={} variants={} status=fallback_missing_config returned={}",
                    model, System.currentTimeMillis() - start, variants, fallbackResult.size());
            return fallbackResult;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (properties.getRewrite().getApiKey() != null && !properties.getRewrite().getApiKey().trim().isEmpty()) {
                headers.setBearerAuth(properties.getRewrite().getApiKey().trim());
            }
            String prompt = buildPrompt(query, variants);
            String url = normalizeChatCompletionsUrl(endpoint);
            Map<String, Object> payload = mapOf(
                    "model", properties.getRewrite().getModel(),
                    "messages", java.util.Arrays.asList(
                            mapOf("role", "system", "content", "你是检索优化助手，请按要求返回改写结果。"),
                            mapOf("role", "user", "content", prompt)
                    ),
                    "temperature", 0.3
            );
            payload.put("enable_thinking", false);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, new HttpEntity<Map<String, Object>>(payload, headers), Map.class);
            List<String> parsed = parseRewrites(response);
            if (parsed.isEmpty()) {
                List<String> fallbackResult = fallback.rewrite(query, variants);
                log.info("[MODEL][rewrite] model={} elapsedMs={} variants={} status=fallback_empty_result returned={}",
                        model, System.currentTimeMillis() - start, variants, fallbackResult.size());
                return fallbackResult;
            }
            log.info("[MODEL][rewrite] model={} elapsedMs={} variants={} status=success returned={}",
                    model, System.currentTimeMillis() - start, variants, parsed.size());
            return parsed;
        } catch (Exception e) {
            List<String> fallbackResult = fallback.rewrite(query, variants);
            log.warn("[MODEL][rewrite] model={} elapsedMs={} variants={} status=fallback_exception error={} returned={}",
                    model, System.currentTimeMillis() - start, variants, e.getMessage(), fallbackResult.size());
            return fallbackResult;
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
        Object choicesObj = response.get("choices");
        if (choicesObj instanceof List && !((List<?>) choicesObj).isEmpty()) {
            Object first = ((List<?>) choicesObj).get(0);
            if (first instanceof Map) {
                Object messageObj = ((Map<?, ?>) first).get("message");
                if (messageObj instanceof Map) {
                    Object contentObj = ((Map<?, ?>) messageObj).get("content");
                    if (contentObj != null) {
                        String[] lines = String.valueOf(contentObj).split("\\r?\\n");
                        for (String line : lines) {
                            String cleaned = line.replaceFirst("^\\s*[-\\d\\.\\)]\\s*", "").trim();
                            if (!cleaned.isEmpty()) {
                                rewrites.add(cleaned);
                            }
                        }
                        if (!rewrites.isEmpty()) {
                            return rewrites;
                        }
                    }
                }
            }
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

    private String normalizeChatCompletionsUrl(String endpoint) {
        String url = endpoint == null ? "" : endpoint.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.endsWith("/chat/completions")) {
            url = url + "/chat/completions";
        }
        return url;
    }

    private Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        java.util.Map<String, Object> out = new java.util.HashMap<String, Object>();
        out.put(k1, v1);
        out.put(k2, v2);
        out.put(k3, v3);
        return out;
    }

    private Map<String, String> mapOf(String k1, String v1, String k2, String v2) {
        java.util.Map<String, String> out = new java.util.HashMap<String, String>();
        out.put(k1, v1);
        out.put(k2, v2);
        return out;
    }
}
