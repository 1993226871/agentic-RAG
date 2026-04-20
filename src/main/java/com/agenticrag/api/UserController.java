package com.agenticrag.api;

import com.agenticrag.api.dto.UpdateProfileRequest;
import com.agenticrag.model.UserProfile;
import com.agenticrag.service.AuthService;
import com.agenticrag.service.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final AuthService authService;
    private final JwtService jwtService;

    public UserController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @GetMapping("/profile")
    public UserProfile profile(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = resolveUserId(authorization);
        UserProfile profile = authService.getProfile(userId);
        if (profile == null) {
            throw new AuthFailedException();
        }
        return profile;
    }

    @PutMapping("/profile")
    public Map<String, Object> updateProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody UpdateProfileRequest request
    ) {
        String userId = resolveUserId(authorization);
        boolean ok = authService.updateProfile(userId, request.getNickname(), request.getEmail(), request.getBio());
        if (!ok) {
            throw new AuthFailedException();
        }
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("ok", true);
        result.put("profile", authService.getProfile(userId));
        return result;
    }

    private String resolveUserId(String authorization) {
        String userId = jwtService.parseUserIdFromHeader(authorization);
        if (userId == null || userId.trim().isEmpty()) {
            throw new AuthFailedException();
        }
        return userId;
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    private static class AuthFailedException extends RuntimeException {
    }
}
