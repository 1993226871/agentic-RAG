package com.agenticrag.infra.memory;

import com.agenticrag.model.MemorySummaryDocument;
import com.agenticrag.ports.ConversationMemoryStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class InMemoryConversationMemoryStore implements ConversationMemoryStore {
    private final List<MemorySummaryDocument> memories = new CopyOnWriteArrayList<MemorySummaryDocument>();

    @Override
    public void save(MemorySummaryDocument document) {
        memories.add(document);
    }

    @Override
    public List<MemorySummaryDocument> search(String userId, String query, int topK) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return memories.stream()
                .filter(m -> userId.equals(m.getUserId()))
                .sorted(Comparator
                        .comparingInt((MemorySummaryDocument m) -> overlapScore(q, m.getSummary(), m.getRawConversation()))
                        .reversed()
                        .thenComparingLong(MemorySummaryDocument::getCreatedAt).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    private int overlapScore(String query, String... texts) {
        if (query.trim().isEmpty()) {
            return 0;
        }
        String[] terms = query.split("\\s+");
        int score = 0;
        for (String text : texts) {
            String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (!term.isEmpty() && lower.contains(term)) {
                    score++;
                }
            }
        }
        return score;
    }

    public List<MemorySummaryDocument> all() {
        return new ArrayList<MemorySummaryDocument>(memories);
    }
}
