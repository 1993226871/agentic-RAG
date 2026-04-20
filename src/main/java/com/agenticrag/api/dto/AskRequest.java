package com.agenticrag.api.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

public class AskRequest {
    @NotBlank
    private String query;

    @Min(1)
    private int topK = 3;

    private String rewriteMode = "multi";
    private Boolean answerThinking;

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
