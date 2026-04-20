package com.agenticrag.api;

import com.agenticrag.api.dto.LoginRequest;
import com.agenticrag.api.dto.RegisterRequest;
import com.agenticrag.service.AuthService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        boolean ok = authService.login(request.getUserId(), request.getPassword());
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("ok", ok);
        result.put("userId", request.getUserId());
        return result;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        boolean ok = authService.register(request.getUserId(), request.getPassword());
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("ok", ok);
        result.put("userId", request.getUserId());
        return result;
    }
}
