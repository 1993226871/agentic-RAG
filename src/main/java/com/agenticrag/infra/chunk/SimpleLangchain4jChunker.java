package com.agenticrag.infra.chunk;

import com.agenticrag.ports.TextChunker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SimpleLangchain4jChunker implements TextChunker {
    private final int chunkSize;
    private final int overlap;

    public SimpleLangchain4jChunker(int chunkSize, int overlap) {
        if (chunkSize <= 0 || overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("Invalid chunk config");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    public List<String> chunk(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> chunks = new ArrayList<>();
        int step = chunkSize - overlap;
        for (int start = 0; start < normalized.length(); start += step) {
            int end = Math.min(start + chunkSize, normalized.length());
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
        }
        return chunks;
    }
}
