package com.agenticrag.api;

import com.agenticrag.api.dto.AskRequest;
import com.agenticrag.api.dto.AskScopedRequest;
import com.agenticrag.api.dto.EndSessionRequest;
import com.agenticrag.model.QaResult;
import com.agenticrag.service.ReActAgentService;
import com.agenticrag.service.AuthService;
import com.agenticrag.service.JwtService;
import com.agenticrag.service.OnlineQaService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import javax.validation.Valid;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/qa")
@Validated
public class QaController {
    private final OnlineQaService onlineQaService;
    private final ReActAgentService reActAgentService;
    private final AuthService authService;
    private final JwtService jwtService;

    public QaController(
            OnlineQaService onlineQaService,
            ReActAgentService reActAgentService,
            AuthService authService,
            JwtService jwtService
    ) {
        this.onlineQaService = onlineQaService;
        this.reActAgentService = reActAgentService;
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/ask")
    public QaResult ask(@Valid @RequestBody AskRequest request, HttpServletRequest httpServletRequest) {
        String userId = resolveUserId(httpServletRequest, null, null);
        return reActAgentService.ask(
                request.getQuery(),
                request.getTopK(),
                userId,
                request.getRewriteMode(),
                request.getAnswerThinking()
        );
    }

    @PostMapping("/ask-scoped")
    public QaResult askScoped(@Valid @RequestBody AskScopedRequest request, HttpServletRequest httpServletRequest) {
        String userId = resolveUserId(httpServletRequest, request.getUserId(), request.getPassword());
        String fileId = userId + ":" + request.getFileMd5().toLowerCase();
        return reActAgentService.askScoped(
                request.getQuery(),
                request.getTopK(),
                fileId,
                userId,
                request.getSessionId(),
                request.getRewriteMode(),
                request.getAnswerThinking()
        );
    }

    @GetMapping("/status-scoped")
    public Map<String, Object> statusScoped(
            HttpServletRequest request,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam("fileMd5") String fileMd5
    ) {
        String loginUserId = resolveUserId(request, userId, password);
        String fileId = loginUserId + ":" + fileMd5.toLowerCase();
        int count = onlineQaService.scopedDocCount(fileId);
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("ready", count > 0);
        out.put("chunkCount", count);
        return out;
    }

    @PostMapping("/end-session")
    public Map<String, Object> endSession(@Valid @RequestBody EndSessionRequest request, HttpServletRequest httpServletRequest) {
        String userId = resolveUserId(httpServletRequest, request.getUserId(), request.getPassword());
        boolean summarized = onlineQaService.endSession(userId, request.getSessionId());
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("ok", true);
        out.put("summarized", summarized);
        return out;
    }

    private String resolveUserId(HttpServletRequest request, String fallbackUserId, String fallbackPassword) {
        String authHeader = request.getHeader("Authorization");
        String userId = jwtService.parseUserIdFromHeader(authHeader);
        if (userId != null && !userId.trim().isEmpty()) {
            return userId;
        }
        if (fallbackUserId != null && fallbackPassword != null && authService.login(fallbackUserId, fallbackPassword)) {
            return fallbackUserId;
        }
        throw new AuthFailedException();
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    private static class AuthFailedException extends RuntimeException {
    }
}
