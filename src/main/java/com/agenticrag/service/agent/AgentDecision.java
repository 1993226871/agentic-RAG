package com.agenticrag.service.agent;

public class AgentDecision {
    public static final String ACTION_RETRIEVE_DOCS = "retrieve_docs";
    public static final String ACTION_RETRIEVE_MEMORY = "retrieve_memory";
    public static final String ACTION_FINISH = "finish";

    private String action;
    private String query;
    private String finalAnswer;
    private double confidence;

    public AgentDecision() {
    }

    public AgentDecision(String action, String query, String finalAnswer, double confidence) {
        this.action = action;
        this.query = query;
        this.finalAnswer = finalAnswer;
        this.confidence = confidence;
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

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}
