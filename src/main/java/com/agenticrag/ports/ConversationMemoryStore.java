package com.agenticrag.ports;

import com.agenticrag.model.MemorySummaryDocument;

import java.util.List;

public interface ConversationMemoryStore {
    void save(MemorySummaryDocument document);

    List<MemorySummaryDocument> search(String userId, String query, int topK);
}
