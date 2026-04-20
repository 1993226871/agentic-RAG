package com.agenticrag.infra.rerank;

import com.agenticrag.model.RetrievedDoc;
import com.agenticrag.ports.Reranker;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class MockBgeReranker implements Reranker {
    @Override
    public List<RetrievedDoc> rerank(String query, List<RetrievedDoc> candidates, int topK) {
        Set<String> queryTerms = Arrays.stream(normalize(query).split("\\s+"))
                .filter(s -> !s.trim().isEmpty())
                .collect(Collectors.toSet());
        return candidates.stream()
                .map(doc -> new RetrievedDoc(doc.document(), doc.score() + overlapBoost(queryTerms, doc.document().text())))
                .sorted(Comparator.comparingDouble(RetrievedDoc::score).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    private static double overlapBoost(Set<String> queryTerms, String text) {
        if (queryTerms.isEmpty()) {
            return 0;
        }
        Set<String> docTerms = Arrays.stream(normalize(text).split("\\s+"))
                .filter(s -> !s.trim().isEmpty())
                .collect(Collectors.toSet());
        long overlap = queryTerms.stream().filter(docTerms::contains).count();
        return overlap * 0.1;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{IsAlphabetic}\\p{IsIdeographic}0-9\\s]", " ");
    }
}
