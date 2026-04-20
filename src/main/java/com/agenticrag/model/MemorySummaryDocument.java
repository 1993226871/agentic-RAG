package com.agenticrag.model;

public class MemorySummaryDocument {
    private String id;
    private String userId;
    private String sessionId;
    private String summary;
    private String rawConversation;
    private long createdAt;

    public MemorySummaryDocument() {
    }

    public MemorySummaryDocument(String id, String userId, String sessionId, String summary, String rawConversation, long createdAt) {
        this.id = id;
        this.userId = userId;
        this.sessionId = sessionId;
        this.summary = summary;
        this.rawConversation = rawConversation;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRawConversation() {
        return rawConversation;
    }

    public void setRawConversation(String rawConversation) {
        this.rawConversation = rawConversation;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
