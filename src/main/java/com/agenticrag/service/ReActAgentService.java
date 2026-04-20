package com.agenticrag.service;

import com.agenticrag.model.MemorySummaryDocument;
import com.agenticrag.model.QaResult;
import com.agenticrag.model.RetrievedDoc;
import com.agenticrag.ports.AgentPlanner;
import com.agenticrag.service.agent.AgentDecision;
import com.agenticrag.service.agent.AgentTraceStep;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReActAgentService {
    private final OnlineQaService onlineQaService;
    private final AgentPlanner agentPlanner;
    private final int maxSteps;
    private final int docTopK;
    private final int memoryTopK;
    private final boolean enabled;

    public ReActAgentService(OnlineQaService onlineQaService, AgentPlanner agentPlanner, com.agenticrag.config.RagProperties properties) {
        this.onlineQaService = onlineQaService;
        this.agentPlanner = agentPlanner;
        this.maxSteps = properties.getAgent().getMaxSteps();
        this.docTopK = properties.getAgent().getDocTopK();
        this.memoryTopK = properties.getAgent().getMemoryTopK();
        this.enabled = properties.getAgent().isEnabled();
    }

    public QaResult askScoped(String query, int topK, String fileId, String userId, String sessionId) {
        return askScoped(query, topK, fileId, userId, sessionId, OnlineQaService.REWRITE_MODE_MULTI);
    }

    public QaResult askScoped(String query, int topK, String fileId, String userId, String sessionId, String rewriteMode) {
        return askScoped(query, topK, fileId, userId, sessionId, rewriteMode, null);
    }

    public QaResult askScoped(
            String query,
            int topK,
            String fileId,
            String userId,
            String sessionId,
            String rewriteMode,
            Boolean answerThinking
    ) {
        if (!enabled) {
            return onlineQaService.askScoped(query, topK, fileId, userId, sessionId, rewriteMode, answerThinking);
        }
        Map<String, RetrievedDoc> evidence = new LinkedHashMap<String, RetrievedDoc>();
        Map<String, MemorySummaryDocument> memories = new LinkedHashMap<String, MemorySummaryDocument>();
        List<AgentTraceStep> traces = new ArrayList<AgentTraceStep>();

        String stopReason = "max_steps";
        String finalAnswer = "";
        int usedSteps = 0;

        for (int step = 1; step <= maxSteps; step++) {
            usedSteps = step;
            AgentDecision decision = agentPlanner.decide(query, buildScratchpad(evidence, memories), step, maxSteps);
            if (decision == null || decision.getAction() == null) {
                decision = new AgentDecision(AgentDecision.ACTION_RETRIEVE_DOCS, query, "", 0.5);
            }
            String actionQuery = safeQuery(decision.getQuery(), query);
            if (AgentDecision.ACTION_RETRIEVE_MEMORY.equals(decision.getAction())) {
                List<MemorySummaryDocument> hits = onlineQaService.retrieveMemories(userId, actionQuery, memoryTopK);
                for (MemorySummaryDocument hit : hits) {
                    memories.put(hit.getId(), hit);
                }
                traces.add(new AgentTraceStep(step, decision.getAction(), actionQuery, evidence.size(), memories.size()));
                continue;
            }
            if (AgentDecision.ACTION_RETRIEVE_DOCS.equals(decision.getAction())) {
                int perRoundTopK = Math.max(topK, docTopK);
                List<RetrievedDoc> docs = onlineQaService.retrieveScopedKnowledge(actionQuery, perRoundTopK, userId, fileId, rewriteMode);
                for (RetrievedDoc doc : docs) {
                    evidence.put(doc.document().chunkId(), doc);
                }
                traces.add(new AgentTraceStep(step, decision.getAction(), actionQuery, evidence.size(), memories.size()));
                continue;
            }
            if (AgentDecision.ACTION_FINISH.equals(decision.getAction())) {
                stopReason = "enough_evidence";
                finalAnswer = decision.getFinalAnswer() == null ? "" : decision.getFinalAnswer();
                traces.add(new AgentTraceStep(step, decision.getAction(), actionQuery, evidence.size(), memories.size()));
                break;
            }
            traces.add(new AgentTraceStep(step, "unknown_action", actionQuery, evidence.size(), memories.size()));
        }

        List<RetrievedDoc> contexts = new ArrayList<RetrievedDoc>(evidence.values());
        List<MemorySummaryDocument> memoryList = new ArrayList<MemorySummaryDocument>(memories.values());
        String synthesizedAnswer = onlineQaService.composeAnswerForAgent(query, contexts, memoryList, answerThinking);
        if (synthesizedAnswer != null && !synthesizedAnswer.trim().isEmpty()) {
            finalAnswer = synthesizedAnswer;
        } else if (finalAnswer.trim().isEmpty()) {
            finalAnswer = "未检索到与问题相关的知识片段。";
        }
        onlineQaService.recordTurn(userId, sessionId, query, finalAnswer);
        return new QaResult(query, contexts, finalAnswer, traces, usedSteps, stopReason);
    }

    public QaResult ask(String query, int topK) {
        return ask(query, topK, "anonymous", OnlineQaService.REWRITE_MODE_MULTI);
    }

    public QaResult ask(String query, int topK, String userId) {
        return ask(query, topK, userId, OnlineQaService.REWRITE_MODE_MULTI);
    }

    public QaResult ask(String query, int topK, String userId, String rewriteMode) {
        return ask(query, topK, userId, rewriteMode, null);
    }

    public QaResult ask(String query, int topK, String userId, String rewriteMode, Boolean answerThinking) {
        return askScoped(query, topK, null, userId, "default", rewriteMode, answerThinking);
    }

    private String buildScratchpad(Map<String, RetrievedDoc> evidence, Map<String, MemorySummaryDocument> memories) {
        StringBuilder sb = new StringBuilder();
        sb.append("证据数量: ").append(evidence.size()).append("\n");
        int index = 1;
        for (RetrievedDoc doc : evidence.values()) {
            if (index > 4) {
                break;
            }
            String text = doc.document().text();
            String shortText = text == null ? "" : (text.length() > 80 ? text.substring(0, 80) + "..." : text);
            sb.append("doc").append(index).append(": ").append(shortText).append("\n");
            index++;
        }
        sb.append("记忆数量: ").append(memories.size()).append("\n");
        index = 1;
        for (MemorySummaryDocument memory : memories.values()) {
            if (index > 3) {
                break;
            }
            String summary = memory.getSummary() == null ? "" : memory.getSummary();
            String shortSummary = summary.length() > 80 ? summary.substring(0, 80) + "..." : summary;
            sb.append("mem").append(index).append(": ").append(shortSummary).append("\n");
            index++;
        }
        return sb.toString();
    }

    private String safeQuery(String candidate, String fallback) {
        if (candidate == null || candidate.trim().isEmpty()) {
            return fallback;
        }
        return candidate.trim();
    }
}
