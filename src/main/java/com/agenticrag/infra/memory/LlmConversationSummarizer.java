package com.agenticrag.infra.memory;

import com.agenticrag.config.RagProperties;
import com.agenticrag.model.ConversationTurn;
import com.agenticrag.ports.ConversationSummarizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LlmConversationSummarizer implements ConversationSummarizer {
    private static final Logger log = LoggerFactory.getLogger(LlmConversationSummarizer.class);
    private final RagProperties properties;
    private final RestTemplate restTemplate;
    private final ConversationSummarizer fallback = new MockConversationSummarizer();

    public LlmConversationSummarizer(RagProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    @Override
    public String summarize(String userId, String sessionId, List<ConversationTurn> turns) {
        long start = System.currentTimeMillis();
        String model = properties.getMemory().getSummaryModel();
        String endpoint = properties.getMemory().getSummaryEndpoint();
        if (endpoint == null || endpoint.trim().isEmpty()) {
            String fallbackSummary = fallback.summarize(userId, sessionId, turns);
            log.info("[MODEL][memory_summary] model={} elapsedMs={} turns={} status=fallback_missing_config",
                    model, System.currentTimeMillis() - start, turns == null ? 0 : turns.size());
            return fallbackSummary;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (properties.getMemory().getApiKey() != null && !properties.getMemory().getApiKey().trim().isEmpty()) {
                headers.setBearerAuth(properties.getMemory().getApiKey().trim());
            }
            String url = normalizeChatCompletionsUrl(endpoint);
            Map<String, Object> body = new HashMap<String, Object>();
            body.put("model", properties.getMemory().getSummaryModel());
            body.put("messages", java.util.Arrays.asList(
                    mapOf("role", "system", "content", "你是对话总结助手，请只输出简洁中文摘要。"),
                    mapOf("role", "user", "content", buildPrompt(userId, sessionId, turns))
            ));
            body.put("temperature", 0.2);
            body.put("enable_thinking", false);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    url,
                    new HttpEntity<Map<String, Object>>(body, headers),
                    Map.class
            );
            if (response != null && response.get("summary") != null) {
                String summary = String.valueOf(response.get("summary")).trim();
                if (!summary.isEmpty()) {
                    log.info("[MODEL][memory_summary] model={} elapsedMs={} turns={} status=success source=summary",
                            model, System.currentTimeMillis() - start, turns == null ? 0 : turns.size());
                    return summary;
                }
            }
            if (response != null && response.get("text") != null) {
                String summary = String.valueOf(response.get("text")).trim();
                if (!summary.isEmpty()) {
                    log.info("[MODEL][memory_summary] model={} elapsedMs={} turns={} status=success source=text",
                            model, System.currentTimeMillis() - start, turns == null ? 0 : turns.size());
                    return summary;
                }
            }
            String content = extractChoiceContent(response);
            if (content != null && !content.trim().isEmpty()) {
                log.info("[MODEL][memory_summary] model={} elapsedMs={} turns={} status=success source=choices.message",
                        model, System.currentTimeMillis() - start, turns == null ? 0 : turns.size());
                return content.trim();
            }
            String fallbackSummary = fallback.summarize(userId, sessionId, turns);
            log.info("[MODEL][memory_summary] model={} elapsedMs={} turns={} status=fallback_empty_result",
                    model, System.currentTimeMillis() - start, turns == null ? 0 : turns.size());
            return fallbackSummary;
        } catch (Exception e) {
            String fallbackSummary = fallback.summarize(userId, sessionId, turns);
            log.warn("[MODEL][memory_summary] model={} elapsedMs={} turns={} status=fallback_exception error={}",
                    model, System.currentTimeMillis() - start, turns == null ? 0 : turns.size(), e.getMessage());
            return fallbackSummary;
        }
    }

    private String buildPrompt(String userId, String sessionId, List<ConversationTurn> turns) {
        StringBuilder history = new StringBuilder();
        for (ConversationTurn turn : turns) {
            history.append(turn.getRole()).append(": ").append(turn.getContent()).append("\n");
        }
        return "请将以下对话总结成可检索的记忆，输出简洁中文摘要（100-200字），保留关键事实、偏好和结论。\n"
                + "userId=" + userId + ", sessionId=" + sessionId + "\n"
                + history.toString();
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

    private String extractChoiceContent(Map<String, Object> response) {
        if (response == null) {
            return "";
        }
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List) || ((List<?>) choicesObj).isEmpty()) {
            return "";
        }
        Object first = ((List<?>) choicesObj).get(0);
        if (!(first instanceof Map)) {
            return "";
        }
        Object messageObj = ((Map<?, ?>) first).get("message");
        if (!(messageObj instanceof Map)) {
            return "";
        }
        Object contentObj = ((Map<?, ?>) messageObj).get("content");
        return contentObj == null ? "" : String.valueOf(contentObj);
    }

    private Map<String, String> mapOf(String k1, String v1, String k2, String v2) {
        Map<String, String> out = new HashMap<String, String>();
        out.put(k1, v1);
        out.put(k2, v2);
        return out;
    }
}
