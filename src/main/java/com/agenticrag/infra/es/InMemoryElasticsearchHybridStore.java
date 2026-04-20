package com.agenticrag.infra.es;

import com.agenticrag.model.ChunkDocument;
import com.agenticrag.model.RetrievedDoc;
import com.agenticrag.ports.HybridSearchStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.Collections;

public class InMemoryElasticsearchHybridStore implements HybridSearchStore {
    private final List<ChunkDocument> docs = new CopyOnWriteArrayList<>();

    public List<ChunkDocument> allDocs() {
        return new ArrayList<>(docs);
    }

    @Override
    public void save(ChunkDocument document) {
        docs.add(document);
    }

    @Override
    public List<RetrievedDoc> knnSearch(List<Double> queryVector, int topK) {
        return docs.stream()
                .map(doc -> new RetrievedDoc(doc, cosineSimilarity(queryVector, doc.vector())))
                .sorted(Comparator.comparingDouble(RetrievedDoc::score).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    @Override
    public List<RetrievedDoc> bm25Search(String query, int topK) {
        String[] queryTerms = tokenize(query);
        if (queryTerms.length == 0) {
            return Collections.emptyList();
        }
        double avgDocLength = docs.stream().mapToInt(doc -> tokenize(doc.text()).length).average().orElse(1.0);
        double k1 = 1.5;
        double b = 0.75;

        Map<String, Integer> documentFrequency = new HashMap<>();
        for (String term : queryTerms) {
            int df = 0;
            for (ChunkDocument doc : docs) {
                if (containsTerm(doc.text(), term)) {
                    df++;
                }
            }
            documentFrequency.put(term, df);
        }
        int totalDocs = Math.max(docs.size(), 1);

        return docs.stream().map(doc -> {
            String[] terms = tokenize(doc.text());
            int docLength = terms.length;
            Map<String, Long> tf = Arrays.stream(terms)
                    .collect(Collectors.groupingBy(t -> t, Collectors.counting()));

            double score = 0.0;
            for (String term : queryTerms) {
                long freq = tf.getOrDefault(term, 0L);
                if (freq == 0) {
                    continue;
                }
                int df = Math.max(documentFrequency.getOrDefault(term, 0), 1);
                double idf = Math.log(1 + (totalDocs - df + 0.5) / (df + 0.5));
                double numerator = freq * (k1 + 1);
                double denominator = freq + k1 * (1 - b + b * (docLength / avgDocLength));
                score += idf * (numerator / denominator);
            }
            return new RetrievedDoc(doc, score);
        })
                .sorted(Comparator.comparingDouble(RetrievedDoc::score).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    @Override
    public int countByFileId(String fileId) {
        return (int) docs.stream()
                .filter(doc -> fileId != null && fileId.equals(doc.fileId()))
                .count();
    }

    private static String[] tokenize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new String[0];
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsIdeographic}0-9\\s]", " ")
                .trim()
                .split("\\s+");
    }

    private static boolean containsTerm(String text, String term) {
        return Arrays.asList(tokenize(text)).contains(term);
    }

    private static double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty() || a.size() != b.size()) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
