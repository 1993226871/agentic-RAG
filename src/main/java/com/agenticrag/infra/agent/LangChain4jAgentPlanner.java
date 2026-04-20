package com.agenticrag.infra.agent;

import com.agenticrag.config.RagProperties;
import com.agenticrag.ports.AgentPlanner;
import com.agenticrag.service.agent.AgentDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class LangChain4jAgentPlanner implements AgentPlanner {
    private static final Logger log = LoggerFactory.getLogger(LangChain4jAgentPlanner.class);
    private final ReActPlanningAiService aiService;
    private final AgentPlanner fallback = new HeuristicAgentPlanner();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RagProperties properties;

    public LangChain4jAgentPlanner(ChatLanguageModel chatLanguageModel, RagProperties properties) {
        this.properties = properties;
        this.aiService = AiServices.builder(ReActPlanningAiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();
    }

    @Override
    public AgentDecision decide(String userQuery, String scratchpad, int step, int maxSteps) {
        long start = System.currentTimeMillis();
        String model = properties.getAgent().getPlannerModel();
        if (chatDisabled()) {
            AgentDecision decision = fallback.decide(userQuery, scratchpad, step, maxSteps);
            log.info("[MODEL][agent_planner] model={} elapsedMs={} step={}/{} status=fallback_missing_config action={}",
                    model, System.currentTimeMillis() - start, step, maxSteps, decision == null ? "null" : decision.getAction());
            return decision;
        }
        try {
            String raw = aiService.decide(userQuery, scratchpad, step, maxSteps);
            AgentDecision parsed = parseDecision(raw, userQuery);
            if (parsed == null || parsed.getAction() == null) {
                AgentDecision decision = fallback.decide(userQuery, scratchpad, step, maxSteps);
                log.info("[MODEL][agent_planner] model={} elapsedMs={} step={}/{} status=fallback_parse_failed action={}",
                        model, System.currentTimeMillis() - start, step, maxSteps, decision == null ? "null" : decision.getAction());
                return decision;
            }
            log.info("[MODEL][agent_planner] model={} elapsedMs={} step={}/{} status=success action={}",
                    model, System.currentTimeMillis() - start, step, maxSteps, parsed.getAction());
            return parsed;
        } catch (Exception e) {
            AgentDecision decision = fallback.decide(userQuery, scratchpad, step, maxSteps);
            log.warn("[MODEL][agent_planner] model={} elapsedMs={} step={}/{} status=fallback_exception error={} action={}",
                    model, System.currentTimeMillis() - start, step, maxSteps, e.getMessage(), decision == null ? "null" : decision.getAction());
            return decision;
        }
    }

    private boolean chatDisabled() {
        String endpoint = properties.getAgent().getPlannerEndpoint();
        return endpoint == null || endpoint.trim().isEmpty();
    }

    @SuppressWarnings("unchecked")
    private AgentDecision parseDecision(String raw, String defaultQuery) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String normalized = extractJson(raw);
        Map<String, Object> map;
        try {
            map = objectMapper.readValue(normalized, Map.class);
        } catch (Exception e) {
            return null;
        }
        Object action = map.get("action");
        if (action == null) {
            return null;
        }
        AgentDecision decision = new AgentDecision();
        decision.setAction(String.valueOf(action));
        Object query = map.get("query");
        decision.setQuery(query == null || String.valueOf(query).trim().isEmpty() ? defaultQuery : String.valueOf(query));
        Object finalAnswer = map.get("final_answer");
        decision.setFinalAnswer(finalAnswer == null ? "" : String.valueOf(finalAnswer));
        Object confidence = map.get("confidence");
        if (confidence instanceof Number) {
            decision.setConfidence(((Number) confidence).doubleValue());
        } else {
            decision.setConfidence(0.5);
        }
        return decision;
    }

    private String extractJson(String raw) {
        String trimmed = raw.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}
