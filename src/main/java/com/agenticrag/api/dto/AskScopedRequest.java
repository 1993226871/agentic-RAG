package com.agenticrag.api.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

public class AskScopedRequest {
    private String userId;

    private String password;

    @NotBlank
    private String fileMd5;

    @NotBlank
    private String query;

    @Min(1)
    private int topK = 3;

    private String sessionId = "default";

    private String rewriteMode = "multi";
    private Boolean answerThinking;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFileMd5() {
        return fileMd5;
    }

    public void setFileMd5(String fileMd5) {
        this.fileMd5 = fileMd5;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRewriteMode() {
        return rewriteMode;
    }

    public void setRewriteMode(String rewriteMode) {
        this.rewriteMode = rewriteMode;
    }

    public Boolean getAnswerThinking() {
        return answerThinking;
    }

    public void setAnswerThinking(Boolean answerThinking) {
        this.answerThinking = answerThinking;
    }
}
