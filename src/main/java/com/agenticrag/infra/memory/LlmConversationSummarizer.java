package com.agenticrag.infra.memory;

import com.agenticrag.config.RagProperties;
import com.agenticrag.model.ConversationTurn;
import com.agenticrag.ports.ConversationSummarizer;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LlmConversationSummarizer implements ConversationSummarizer {
    private final RagProperties properties;
    private final RestTemplate restTemplate;
    private final ConversationSummarizer fallback = new MockConversationSummarizer();

    public LlmConversationSummarizer(RagProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    @Override
    public String summarize(String userId, String sessionId, List<ConversationTurn> turns) {
        String endpoint = properties.getMemory().getSummaryEndpoint();
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return fallback.summarize(userId, sessionId, turns);
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (properties.getMemory().getApiKey() != null && !properties.getMemory().getApiKey().trim().isEmpty()) {
                headers.setBearerAuth(properties.getMemory().getApiKey().trim());
            }
            Map<String, Object> body = new HashMap<String, Object>();
            body.put("model", properties.getMemory().getSummaryModel());
            body.put("prompt", buildPrompt(userId, sessionId, turns));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    endpoint,
                    new HttpEntity<Map<String, Object>>(body, headers),
                    Map.class
            );
            if (response != null && response.get("summary") != null) {
                String summary = String.valueOf(response.get("summary")).trim();
                if (!summary.isEmpty()) {
                    return summary;
                }
            }
            if (response != null && response.get("text") != null) {
                String summary = String.valueOf(response.get("text")).trim();
                if (!summary.isEmpty()) {
                    return summary;
                }
            }
            return fallback.summarize(userId, sessionId, turns);
        } catch (Exception e) {
            return fallback.summarize(userId, sessionId, turns);
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
}
