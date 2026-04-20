package com.agenticrag.service;

import com.agenticrag.model.UserProfile;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AuthService {
    private final ConcurrentMap<String, UserAccount> users = new ConcurrentHashMap<String, UserAccount>();

    public AuthService() {
        users.put("root", new UserAccount(
                "123456",
                new UserProfile("root", "系统管理员", "root@example.com", "默认管理员账号")
        ));
    }

    public boolean login(String userId, String password) {
        if (userId == null || password == null) {
            return false;
        }
        UserAccount account = users.get(userId);
        return account != null && password.equals(account.password);
    }

    public boolean register(String userId, String password) {
        if (userId == null || userId.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return false;
        }
        String normalized = userId.trim();
        UserAccount account = new UserAccount(
                password,
                new UserProfile(normalized, normalized, "", "")
        );
        return users.putIfAbsent(normalized, account) == null;
    }

    public UserProfile getProfile(String userId) {
        UserAccount account = users.get(userId);
        if (account == null) {
            return null;
        }
        UserProfile src = account.profile;
        return new UserProfile(src.getUserId(), src.getNickname(), src.getEmail(), src.getBio());
    }

    public boolean updateProfile(String userId, String nickname, String email, String bio) {
        UserAccount account = users.get(userId);
        if (account == null) {
            return false;
        }
        if (nickname != null) {
            account.profile.setNickname(nickname);
        }
        if (email != null) {
            account.profile.setEmail(email);
        }
        if (bio != null) {
            account.profile.setBio(bio);
        }
        return true;
    }

    private static class UserAccount {
        private final String password;
        private final UserProfile profile;

        private UserAccount(String password, UserProfile profile) {
            this.password = password;
            this.profile = profile;
        }
    }
}
