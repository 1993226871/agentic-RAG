package com.agenticrag.model;

public class UserProfile {
    private String userId;
    private String nickname;
    private String email;
    private String bio;

    public UserProfile() {
    }

    public UserProfile(String userId, String nickname, String email, String bio) {
        this.userId = userId;
        this.nickname = nickname;
        this.email = email;
        this.bio = bio;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}
