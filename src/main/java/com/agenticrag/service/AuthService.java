package com.agenticrag.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private final Map<String, String> users = new ConcurrentHashMap<String, String>();

    public AuthService() {
        users.put("root", "123456");
    }

    public boolean login(String userId, String password) {
        if (userId == null || password == null) {
            return false;
        }
        String real = users.get(userId);
        return password.equals(real);
    }

    public boolean register(String userId, String password) {
        if (userId == null || userId.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return false;
        }
        return users.putIfAbsent(userId, password) == null;
    }
}
