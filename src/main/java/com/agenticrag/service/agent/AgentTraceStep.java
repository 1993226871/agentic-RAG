package com.agenticrag.service.agent;

public class AgentTraceStep {
    private int step;
    private String action;
    private String query;
    private int evidenceCount;
    private int memoryCount;

    public AgentTraceStep() {
    }

    public AgentTraceStep(int step, String action, String query, int evidenceCount, int memoryCount) {
        this.step = step;
        this.action = action;
        this.query = query;
        this.evidenceCount = evidenceCount;
        this.memoryCount = memoryCount;
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getEvidenceCount() {
        return evidenceCount;
    }

    public void setEvidenceCount(int evidenceCount) {
        this.evidenceCount = evidenceCount;
    }

    public int getMemoryCount() {
        return memoryCount;
    }

    public void setMemoryCount(int memoryCount) {
        this.memoryCount = memoryCount;
    }
}
