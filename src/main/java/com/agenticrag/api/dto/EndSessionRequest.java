package com.agenticrag.api.dto;

import javax.validation.constraints.NotBlank;

public class EndSessionRequest {
    @NotBlank
    private String userId;

    @NotBlank
    private String password;

    private String sessionId = "default";

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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
