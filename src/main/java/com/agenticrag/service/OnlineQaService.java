package com.agenticrag.service;

import com.agenticrag.model.QaResult;
import com.agenticrag.model.RetrievedDoc;
import com.agenticrag.model.ConversationTurn;
import com.agenticrag.model.MemorySummaryDocument;
import com.agenticrag.ports.ConversationMemoryStore;
import com.agenticrag.ports.ConversationSummarizer;
import com.agenticrag.ports.EmbeddingClient;
import com.agenticrag.ports.HybridSearchStore;
import com.agenticrag.ports.QueryRewriter;
import com.agenticrag.ports.Reranker;
import com.agenticrag.infra.memory.InMemoryConversationMemoryStore;
import com.agenticrag.infra.memory.MockConversationSummarizer;
import com.agenticrag.infra.rewrite.MockQueryRewriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class OnlineQaService {
    private final EmbeddingClient embeddingClient;
    private final HybridSearchStore hybridSearchStore;
    private final Reranker reranker;
    private final QueryRewriter queryRewriter;
    private final ConversationMemoryStore memoryStore;
    private final ConversationSummarizer conversationSummarizer;
    private final int rrfK;
    private final int rewriteVariants;
    private final int memoryTopK;
    private final int summarizeEveryTurns;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<String, SessionState>();

    public OnlineQaService(
            EmbeddingClient embeddingClient,
            HybridSearchStore hybridSearchStore,
            Reranker reranker,
            QueryRewriter queryRewriter,
            ConversationMemoryStore memoryStore,
            ConversationSummarizer conversationSummarizer,
            @Value("${rag.search.rrf-k:60}") int rrfK,
            @Value("${rag.rewrite.variants:3}") int rewriteVariants,
            @Value("${rag.memory.history-top-k:3}") int memoryTopK,
            @Value("${rag.memory.summarize-every-turns:20}") int summarizeEveryTurns
    ) {
        this.embeddingClient = embeddingClient;
        this.hybridSearchStore = hybridSearchStore;
        this.reranker = reranker;
        this.queryRewriter = queryRewriter;
        this.memoryStore = memoryStore;
        this.conversationSummarizer = conversationSummarizer;
        this.rrfK = rrfK;
        this.rewriteVariants = rewriteVariants;
        this.memoryTopK = memoryTopK;
        this.summarizeEveryTurns = summarizeEveryTurns;
    }

    public OnlineQaService(
            EmbeddingClient embeddingClient,
            HybridSearchStore hybridSearchStore,
            Reranker reranker,
            int rrfK
    ) {
        this(
                embeddingClient,
                hybridSearchStore,
                reranker,
                new MockQueryRewriter(),
                new InMemoryConversationMemoryStore(),
                new MockConversationSummarizer(),
                rrfK,
                3,
                3,
                20
        );
    }

    public QaResult ask(String query, int topK) {
        List<RetrievedDoc> reranked = searchWithRewrite(query, topK, null);
        String answer = composeAnswer(query, reranked, Collections.<MemorySummaryDocument>emptyList());
        return new QaResult(query, reranked, answer);
    }

    public QaResult askScoped(String query, int topK, String fileId) {
        return askScoped(query, topK, fileId, "anonymous", "default");
    }

    public QaResult askScoped(String query, int topK, String fileId, String userId, String sessionId) {
        List<MemorySummaryDocument> memories = memoryStore.search(userId, query, memoryTopK);
        String systemPrompt = buildSystemPrompt(memories);
        List<RetrievedDoc> reranked = searchWithRewrite(systemPrompt + "\n用户问题: " + query, topK, fileId);
        String answer = composeAnswer(query, reranked, memories);
        recordConversation(userId, sessionId, query, answer);
        summarizeIfNeeded(userId, sessionId);
        return new QaResult(query, reranked, answer);
    }

    public int scopedDocCount(String fileId) {
        return hybridSearchStore.countByFileId(fileId);
    }

    public List<RetrievedDoc> retrieveScopedKnowledge(String query, int topK, String fileId) {
        return searchWithRewrite(query, topK, fileId);
    }

    public List<MemorySummaryDocument> retrieveMemories(String userId, String query, int topK) {
        return memoryStore.search(userId, query, topK);
    }

    public String systemPromptFromMemories(List<MemorySummaryDocument> memories) {
        return buildSystemPrompt(memories);
    }

    public String composeAnswerForAgent(String query, List<RetrievedDoc> docs, List<MemorySummaryDocument> memories) {
        return composeAnswer(query, docs, memories);
    }

    public void recordTurn(String userId, String sessionId, String query, String answer) {
        recordConversation(userId, sessionId, query, answer);
        summarizeIfNeeded(userId, sessionId);
    }

    public boolean endSession(String userId, String sessionId) {
        return summarizeSession(userId, sessionId, true);
    }

    private List<RetrievedDoc> searchWithRewrite(String query, int topK, String fileId) {
        int finalTopK = topK * 3;
        int recallSize = Math.max(finalTopK * 3, 60);
        List<String> allQueries = collectQueries(query);
        List<List<RetrievedDoc>> rankedLists = new ArrayList<List<RetrievedDoc>>();
        for (String candidate : allQueries) {
            List<Double> vector = embeddingClient.embed(candidate);
            List<RetrievedDoc> knn = hybridSearchStore.knnSearch(vector, recallSize);
            List<RetrievedDoc> bm25 = hybridSearchStore.bm25Search(candidate, recallSize);
            rankedLists.add(filterScoped(knn, fileId));
            rankedLists.add(filterScoped(bm25, fileId));
        }
        List<RetrievedDoc> fused = reciprocalRankFusion(rankedLists);
        List<RetrievedDoc> limited = fused.size() > finalTopK ? fused.subList(0, finalTopK) : fused;
        return reranker.rerank(query, limited, finalTopK);
    }

    private List<String> collectQueries(String query) {
        LinkedHashSet<String> queries = new LinkedHashSet<String>();
        if (query != null && !query.trim().isEmpty()) {
            queries.add(query.trim());
        }
        List<String> rewrites = queryRewriter.rewrite(query, rewriteVariants);
        if (rewrites != null) {
            for (String rewrite : rewrites) {
                if (rewrite != null && !rewrite.trim().isEmpty()) {
                    queries.add(rewrite.trim());
                }
            }
        }
        return new ArrayList<String>(queries);
    }

    private List<RetrievedDoc> filterScoped(List<RetrievedDoc> docs, String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            return docs;
        }
        return docs.stream()
                .filter(doc -> fileId.equals(doc.document().fileId()))
                .collect(Collectors.toList());
    }

    private List<RetrievedDoc> reciprocalRankFusion(List<List<RetrievedDoc>> rankedLists) {
        Map<String, RetrievedDoc> byId = new HashMap<>();
        Map<String, Double> fusedScore = new HashMap<>();

        for (List<RetrievedDoc> ranked : rankedLists) {
            for (int i = 0; i < ranked.size(); i++) {
                RetrievedDoc doc = ranked.get(i);
                String chunkId = doc.document().chunkId();
                byId.putIfAbsent(chunkId, doc);
                fusedScore.merge(chunkId, 1.0 / (rrfK + i + 1), Double::sum);
            }
        }

        List<RetrievedDoc> result = new ArrayList<>();
        for (Map.Entry<String, Double> entry : fusedScore.entrySet()) {
            result.add(new RetrievedDoc(byId.get(entry.getKey()).document(), entry.getValue()));
        }
        result.sort(Comparator.comparingDouble(RetrievedDoc::score).reversed());
        return result;
    }

    private String composeAnswer(String query, List<RetrievedDoc> docs, List<MemorySummaryDocument> memories) {
        if (docs.isEmpty()) {
            return "未检索到与问题相关的知识片段。";
        }
        StringBuilder sb = new StringBuilder("基于检索到的知识，问题“")
                .append(query)
                .append("”可参考：");
        for (int i = 0; i < docs.size(); i++) {
            String text = docs.get(i).document().text();
            String shortText = text.length() > 60 ? text.substring(0, 60) + "..." : text;
            sb.append(" [").append(i + 1).append("] ").append(shortText);
        }
        if (memories != null && !memories.isEmpty()) {
            sb.append(" | 历史记忆命中 ").append(memories.size()).append(" 条摘要。");
        }
        return sb.toString();
    }

    private String buildSystemPrompt(List<MemorySummaryDocument> memories) {
        if (memories == null || memories.isEmpty()) {
            return "你是企业知识库问答助手，请优先基于检索结果回答。";
        }
        StringBuilder sb = new StringBuilder("你是企业知识库问答助手。以下是与当前问题相关的历史摘要，请结合使用：\n");
        for (int i = 0; i < memories.size(); i++) {
            sb.append(i + 1).append(". ").append(memories.get(i).getSummary()).append("\n");
        }
        return sb.toString();
    }

    private void recordConversation(String userId, String sessionId, String query, String answer) {
        SessionState state = sessions.computeIfAbsent(sessionKey(userId, sessionId), k -> new SessionState());
        long now = System.currentTimeMillis();
        state.turns.add(new ConversationTurn("user", query, now));
        state.turns.add(new ConversationTurn("assistant", answer, now));
    }

    private void summarizeIfNeeded(String userId, String sessionId) {
        summarizeSession(userId, sessionId, false);
    }

    private boolean summarizeSession(String userId, String sessionId, boolean force) {
        SessionState state = sessions.get(sessionKey(userId, sessionId));
        if (state == null) {
            return false;
        }
        int newTurns = state.turns.size() - state.summarizedOffset;
        if (!force && newTurns < summarizeEveryTurns) {
            return false;
        }
        if (newTurns <= 0) {
            if (force) {
                sessions.remove(sessionKey(userId, sessionId));
            }
            return false;
        }
        List<ConversationTurn> freshTurns = new ArrayList<ConversationTurn>(state.turns.subList(state.summarizedOffset, state.turns.size()));
        String summary = conversationSummarizer.summarize(userId, sessionId, freshTurns);
        if (summary != null && !summary.trim().isEmpty()) {
            MemorySummaryDocument doc = new MemorySummaryDocument(
                    UUID.randomUUID().toString(),
                    userId,
                    sessionId,
                    summary,
                    toRawConversation(freshTurns),
                    System.currentTimeMillis()
            );
            memoryStore.save(doc);
        }
        state.summarizedOffset = state.turns.size();
        if (force) {
            sessions.remove(sessionKey(userId, sessionId));
        }
        return true;
    }

    private String toRawConversation(List<ConversationTurn> turns) {
        StringBuilder sb = new StringBuilder();
        for (ConversationTurn turn : turns) {
            sb.append(turn.getRole()).append(": ").append(turn.getContent()).append("\n");
        }
        return sb.toString();
    }

    private String sessionKey(String userId, String sessionId) {
        String safeUserId = userId == null ? "anonymous" : userId;
        String safeSessionId = (sessionId == null || sessionId.trim().isEmpty()) ? "default" : sessionId;
        return safeUserId + "::" + safeSessionId;
    }

    private static class SessionState {
        private final List<ConversationTurn> turns = new ArrayList<ConversationTurn>();
        private int summarizedOffset = 0;
    }
}
