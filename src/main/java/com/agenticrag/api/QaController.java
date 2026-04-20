package com.agenticrag.api;

import com.agenticrag.api.dto.AskRequest;
import com.agenticrag.api.dto.AskScopedRequest;
import com.agenticrag.api.dto.EndSessionRequest;
import com.agenticrag.model.QaResult;
import com.agenticrag.service.ReActAgentService;
import com.agenticrag.service.AuthService;
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
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/qa")
@Validated
public class QaController {
    private final OnlineQaService onlineQaService;
    private final ReActAgentService reActAgentService;
    private final AuthService authService;

    public QaController(OnlineQaService onlineQaService, ReActAgentService reActAgentService, AuthService authService) {
        this.onlineQaService = onlineQaService;
        this.reActAgentService = reActAgentService;
        this.authService = authService;
    }

    @PostMapping("/ask")
    public QaResult ask(@Valid @RequestBody AskRequest request) {
        return reActAgentService.ask(request.getQuery(), request.getTopK());
    }

    @PostMapping("/ask-scoped")
    public QaResult askScoped(@Valid @RequestBody AskScopedRequest request) {
        if (!authService.login(request.getUserId(), request.getPassword())) {
            throw new AuthFailedException();
        }
        String fileId = request.getUserId() + ":" + request.getFileMd5().toLowerCase();
        return reActAgentService.askScoped(
                request.getQuery(),
                request.getTopK(),
                fileId,
                request.getUserId(),
                request.getSessionId()
        );
    }

    @GetMapping("/status-scoped")
    public Map<String, Object> statusScoped(
            @RequestParam("userId") String userId,
            @RequestParam("password") String password,
            @RequestParam("fileMd5") String fileMd5
    ) {
        if (!authService.login(userId, password)) {
            throw new AuthFailedException();
        }
        String fileId = userId + ":" + fileMd5.toLowerCase();
        int count = onlineQaService.scopedDocCount(fileId);
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("ready", count > 0);
        out.put("chunkCount", count);
        return out;
    }

    @PostMapping("/end-session")
    public Map<String, Object> endSession(@Valid @RequestBody EndSessionRequest request) {
        if (!authService.login(request.getUserId(), request.getPassword())) {
            throw new AuthFailedException();
        }
        boolean summarized = onlineQaService.endSession(request.getUserId(), request.getSessionId());
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("ok", true);
        out.put("summarized", summarized);
        return out;
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    private static class AuthFailedException extends RuntimeException {
    }
}
