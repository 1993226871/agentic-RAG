package com.agenticrag.config;

import com.agenticrag.service.JwtService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {
    private final JwtService jwtService;

    public JwtAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String userId = jwtService.parseUserIdFromHeader(request.getHeader("Authorization"));
        if (userId == null || userId.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write("{\"message\":\"Unauthorized: invalid or missing token\"}".getBytes(StandardCharsets.UTF_8));
            return false;
        }
        request.setAttribute("jwtUserId", userId);
        return true;
    }
}
