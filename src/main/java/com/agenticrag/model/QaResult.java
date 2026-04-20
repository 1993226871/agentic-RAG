package com.agenticrag.model;

import java.util.List;
import com.agenticrag.service.agent.AgentTraceStep;

public class QaResult {
    private String query;
    private List<RetrievedDoc> contexts;
    private String answer;
    private List<AgentTraceStep> agentTrace;
    private int stepsUsed;
    private String stopReason;

    public QaResult() {
    }

    public QaResult(String query, List<RetrievedDoc> contexts, String answer) {
        this.query = query;
        this.contexts = contexts;
        this.answer = answer;
    }

    public QaResult(String query, List<RetrievedDoc> contexts, String answer, List<AgentTraceStep> agentTrace, int stepsUsed, String stopReason) {
        this.query = query;
        this.contexts = contexts;
        this.answer = answer;
        this.agentTrace = agentTrace;
        this.stepsUsed = stepsUsed;
        this.stopReason = stopReason;
    }

    public String query() {
        return query;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<RetrievedDoc> contexts() {
        return contexts;
    }

    public List<RetrievedDoc> getContexts() {
        return contexts;
    }

    public void setContexts(List<RetrievedDoc> contexts) {
        this.contexts = contexts;
    }

    public String answer() {
        return answer;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<AgentTraceStep> getAgentTrace() {
        return agentTrace;
    }

    public void setAgentTrace(List<AgentTraceStep> agentTrace) {
        this.agentTrace = agentTrace;
    }

    public int getStepsUsed() {
        return stepsUsed;
    }

    public void setStepsUsed(int stepsUsed) {
        this.stepsUsed = stepsUsed;
    }

    public String getStopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }
}
