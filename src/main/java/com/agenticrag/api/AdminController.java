package com.agenticrag.api;

import com.agenticrag.service.AsyncIngestionConsumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "true", matchIfMissing = true)
public class AdminController {
    private final AsyncIngestionConsumer ingestionConsumer;

    public AdminController(AsyncIngestionConsumer ingestionConsumer) {
        this.ingestionConsumer = ingestionConsumer;
    }

    @PostMapping("/consume-once")
    public Map<String, Object> consumeOnce() {
        int inserted = ingestionConsumer.consumeOnce();
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("insertedChunks", inserted);
        return result;
    }
}
