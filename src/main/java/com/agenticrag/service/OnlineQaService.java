package com.agenticrag.service;

import com.agenticrag.config.RagProperties;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Service
public class OnlineQaService {
    private static final Logger log = LoggerFactory.getLogger(OnlineQaService.class);
    public static final String REWRITE_MODE_MULTI = "multi";
    public static final String REWRITE_MODE_SINGLE = "single";
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private RagProperties ragProperties;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    @Autowired
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
        return ask(query, topK, "anonymous", REWRITE_MODE_MULTI);
    }

    public QaResult ask(String query, int topK, String userId) {
        return ask(query, topK, userId, REWRITE_MODE_MULTI);
    }

    public QaResult ask(String query, int topK, String userId, String rewriteMode) {
        List<RetrievedDoc> reranked = searchWithRewrite(query, topK, userId, null, rewriteMode);
        String answer = composeAnswer(query, reranked, Collections.<MemorySummaryDocument>emptyList());
        return new QaResult(query, reranked, answer);
    }

    public QaResult askScoped(String query, int topK, String fileId) {
        return askScoped(query, topK, fileId, "anonymous", "default", REWRITE_MODE_MULTI);
    }

    public QaResult askScoped(String query, int topK, String fileId, String userId, String sessionId) {
        return askScoped(query, topK, fileId, userId, sessionId, REWRITE_MODE_MULTI);
    }

    public QaResult askScoped(String query, int topK, String fileId, String userId, String sessionId, String rewriteMode) {
        return askScoped(query, topK, fileId, userId, sessionId, rewriteMode, null);
    }

    public QaResult askScoped(
            String query,
            int topK,
            String fileId,
            String userId,
            String sessionId,
            String rewriteMode,
            Boolean answerThinking
    ) {
        List<MemorySummaryDocument> memories = memoryStore.search(userId, query, memoryTopK);
        String systemPrompt = buildSystemPrompt(memories);
        List<RetrievedDoc> reranked = searchWithRewrite(systemPrompt + "\n用户问题: " + query, topK, userId, fileId, rewriteMode);
        String answer = composeAnswerForAgent(query, reranked, memories, answerThinking);
        recordConversation(userId, sessionId, query, answer);
        summarizeIfNeeded(userId, sessionId);
        return new QaResult(query, reranked, answer);
    }

    public int scopedDocCount(String fileId) {
        return hybridSearchStore.countByFileId(fileId);
    }

    public List<RetrievedDoc> retrieveScopedKnowledge(String query, int topK, String fileId) {
        return retrieveScopedKnowledge(query, topK, "anonymous", fileId, REWRITE_MODE_MULTI);
    }

    public List<RetrievedDoc> retrieveScopedKnowledge(String query, int topK, String userId, String fileId) {
        return retrieveScopedKnowledge(query, topK, userId, fileId, REWRITE_MODE_MULTI);
    }

    public List<RetrievedDoc> retrieveScopedKnowledge(String query, int topK, String userId, String fileId, String rewriteMode) {
        return searchWithRewrite(query, topK, userId, fileId, rewriteMode);
    }

    public List<MemorySummaryDocument> retrieveMemories(String userId, String query, int topK) {
        return memoryStore.search(userId, query, topK);
    }

    public String systemPromptFromMemories(List<MemorySummaryDocument> memories) {
        return buildSystemPrompt(memories);
    }

    public String composeAnswerForAgent(String query, List<RetrievedDoc> docs, List<MemorySummaryDocument> memories) {
        return composeAnswerForAgent(query, docs, memories, null);
    }

    public String composeAnswerForAgent(String query, List<RetrievedDoc> docs, List<MemorySummaryDocument> memories, Boolean answerThinking) {
        if (docs == null || docs.isEmpty()) {
            return composeAnswer(query, docs == null ? Collections.<RetrievedDoc>emptyList() : docs, memories);
        }
        String llmAnswer = generateAnswerByLlm(query, docs, memories, answerThinking);
        if (llmAnswer != null && !llmAnswer.trim().isEmpty()) {
            return llmAnswer.trim();
        }
        if (!isAnswerLlmConfigured()) {
            return "当前未配置问答生成模型（请设置 DASHSCOPE_API_KEY 或 rag.agent.api-key），仅完成了检索，暂无法生成最终回答。";
        }
        return composeAnswer(query, docs, memories);
    }

    public void recordTurn(String userId, String sessionId, String query, String answer) {
        recordConversation(userId, sessionId, query, answer);
        summarizeIfNeeded(userId, sessionId);
    }

    public boolean endSession(String userId, String sessionId) {
        return summarizeSession(userId, sessionId, true);
    }

    private List<RetrievedDoc> searchWithRewrite(String query, int topK, String userId, String fileId, String rewriteMode) {
        int finalTopK = topK * 3;
        int recallSize = Math.max(finalTopK * 3, 60);
        List<String> allQueries = collectQueries(query, rewriteMode);
        List<List<RetrievedDoc>> rankedLists = new ArrayList<List<RetrievedDoc>>();
        for (String candidate : allQueries) {
            List<Double> vector = embeddingClient.embed(candidate);
            List<RetrievedDoc> knn = hybridSearchStore.knnSearch(vector, recallSize);
            List<RetrievedDoc> bm25 = hybridSearchStore.bm25Search(candidate, recallSize);
            rankedLists.add(filterScopedAndUser(knn, userId, fileId));
            rankedLists.add(filterScopedAndUser(bm25, userId, fileId));
        }
        List<RetrievedDoc> fused = reciprocalRankFusion(rankedLists);
        List<RetrievedDoc> limited = fused.size() > finalTopK ? fused.subList(0, finalTopK) : fused;
        return reranker.rerank(query, limited, finalTopK);
    }

    private List<String> collectQueries(String query, String rewriteMode) {
        String normalizedMode = normalizeRewriteMode(rewriteMode);
        LinkedHashSet<String> queries = new LinkedHashSet<String>();
        String trimmedQuery = query == null ? "" : query.trim();
        if (trimmedQuery.isEmpty()) {
            return new ArrayList<String>(queries);
        }
        if (REWRITE_MODE_SINGLE.equals(normalizedMode)) {
            List<String> rewrites = queryRewriter.rewrite(trimmedQuery, 1);
            if (rewrites != null) {
                for (String rewrite : rewrites) {
                    if (rewrite != null && !rewrite.trim().isEmpty()) {
                        queries.add(rewrite.trim());
                        break;
                    }
                }
            }
            if (queries.isEmpty()) {
                queries.add(trimmedQuery);
            }
            return new ArrayList<String>(queries);
        }
        queries.add(trimmedQuery);
        List<String> rewrites = queryRewriter.rewrite(trimmedQuery, rewriteVariants);
        if (rewrites != null) {
            for (String rewrite : rewrites) {
                if (rewrite != null && !rewrite.trim().isEmpty()) {
                    queries.add(rewrite.trim());
                }
            }
        }
        return new ArrayList<String>(queries);
    }

    private String normalizeRewriteMode(String rewriteMode) {
        if (rewriteMode == null || rewriteMode.trim().isEmpty()) {
            return REWRITE_MODE_MULTI;
        }
        String mode = rewriteMode.trim().toLowerCase();
        if (REWRITE_MODE_SINGLE.equals(mode)) {
            return REWRITE_MODE_SINGLE;
        }
        return REWRITE_MODE_MULTI;
    }

    private List<RetrievedDoc> filterScopedAndUser(List<RetrievedDoc> docs, String userId, String fileId) {
        String userPrefix = normalizeUserPrefix(userId);
        return docs.stream()
                .filter(doc -> {
                    String currentFileId = doc.document().fileId();
                    if (currentFileId == null || currentFileId.trim().isEmpty()) {
                        return false;
                    }
                    if (fileId != null && !fileId.trim().isEmpty()) {
                        return fileId.equals(currentFileId);
                    }
                    if (userPrefix == null) {
                        return true;
                    }
                    return currentFileId.startsWith(userPrefix);
                })
                .collect(Collectors.toList());
    }

    private String normalizeUserPrefix(String userId) {
        if (userId == null || userId.trim().isEmpty() || "anonymous".equalsIgnoreCase(userId.trim())) {
            return null;
        }
        return userId.trim() + ":";
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

    private String generateAnswerByLlm(String query, List<RetrievedDoc> docs, List<MemorySummaryDocument> memories, Boolean answerThinking) {
        long start = System.currentTimeMillis();
        if (docs == null || docs.isEmpty()) {
            return null;
        }
        if (ragProperties == null || restTemplate == null) {
            log.warn("[MODEL][answer] model=unknown elapsedMs={} docs={} status=skip_missing_dependency",
                    System.currentTimeMillis() - start, docs.size());
            return null;
        }
        String endpoint = ragProperties.getAgent().getPlannerEndpoint();
        String model = ragProperties.getAgent().getAnswerModel();
        if (model == null || model.trim().isEmpty()) {
            model = ragProperties.getAgent().getPlannerModel();
        }
        String apiKey = resolveAnswerApiKey();
        if (endpoint == null || endpoint.trim().isEmpty() || model == null || model.trim().isEmpty()) {
            log.warn("[MODEL][answer] model={} elapsedMs={} docs={} status=skip_missing_endpoint_or_model",
                    model, System.currentTimeMillis() - start, docs.size());
            return null;
        }
        if (apiKey == null || apiKey.trim().isEmpty() || "EMPTY_API_KEY".equals(apiKey.trim())) {
            log.warn("[MODEL][answer] model={} elapsedMs={} docs={} status=skip_missing_api_key",
                    model, System.currentTimeMillis() - start, docs.size());
            return null;
        }
        try {
            String url = endpoint.trim();
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            if (!url.endsWith("/chat/completions")) {
                url = url + "/chat/completions";
            }

            Map<String, Object> body = new HashMap<String, Object>();
            body.put("model", model.trim());
            body.put("temperature", 0.2);
            body.put("enable_thinking", resolveAnswerThinking(answerThinking));
            body.put("messages", buildAnswerMessages(query, docs, memories));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey.trim());

            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<Map<String, Object>>(body, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                String content = parseAssistantText(choices.get(0));
                if (content != null && !content.trim().isEmpty()) {
                    log.info("[MODEL][answer] model={} elapsedMs={} docs={} status=success source=choices.message",
                            model, System.currentTimeMillis() - start, docs.size());
                    return content.trim();
                }
            }
            String outputText = root.path("output_text").asText("");
            if (outputText != null && !outputText.trim().isEmpty()) {
                log.info("[MODEL][answer] model={} elapsedMs={} docs={} status=success source=output_text",
                        model, System.currentTimeMillis() - start, docs.size());
                return outputText.trim();
            }
            log.warn("[MODEL][answer] model={} elapsedMs={} docs={} status=empty_response_text body={}",
                    model, System.currentTimeMillis() - start, docs.size(), response.getBody());
            return null;
        } catch (Exception e) {
            log.warn("[MODEL][answer] model={} elapsedMs={} docs={} status=exception error={}",
                    model, System.currentTimeMillis() - start, docs.size(), e.getMessage());
            return null;
        }
    }

    private boolean resolveAnswerThinking(Boolean answerThinking) {
        return answerThinking == null ? ragProperties.getAgent().isAnswerThinking() : answerThinking.booleanValue();
    }

    private String resolveAnswerApiKey() {
        String agentApiKey = ragProperties.getAgent().getApiKey();
        if (agentApiKey != null && !agentApiKey.trim().isEmpty() && !"EMPTY_API_KEY".equals(agentApiKey.trim())) {
            return agentApiKey.trim();
        }
        String embeddingApiKey = ragProperties.getEmbedding().getApiKey();
        if (embeddingApiKey != null && !embeddingApiKey.trim().isEmpty() && !"EMPTY_API_KEY".equals(embeddingApiKey.trim())) {
            return embeddingApiKey.trim();
        }
        String rerankApiKey = ragProperties.getRerank().getApiKey();
        if (rerankApiKey != null && !rerankApiKey.trim().isEmpty() && !"EMPTY_API_KEY".equals(rerankApiKey.trim())) {
            return rerankApiKey.trim();
        }
        return null;
    }

    private boolean isAnswerLlmConfigured() {
        if (ragProperties == null) {
            return false;
        }
        String endpoint = ragProperties.getAgent().getPlannerEndpoint();
        String model = ragProperties.getAgent().getPlannerModel();
        String apiKey = resolveAnswerApiKey();
        return endpoint != null && !endpoint.trim().isEmpty()
                && model != null && !model.trim().isEmpty()
                && apiKey != null && !apiKey.trim().isEmpty()
                && !"EMPTY_API_KEY".equals(apiKey.trim());
    }

    private String parseAssistantText(JsonNode choice) {
        if (choice == null || choice.isMissingNode()) {
            return "";
        }
        JsonNode message = choice.path("message");
        JsonNode contentNode = message.path("content");
        String textFromContent = parseContentNode(contentNode);
        if (!textFromContent.isEmpty()) {
            return textFromContent;
        }
        String text = choice.path("text").asText("");
        if (text != null && !text.trim().isEmpty()) {
            return text.trim();
        }
        String reasoning = message.path("reasoning_content").asText("");
        return reasoning == null ? "" : reasoning.trim();
    }

    private String parseContentNode(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isTextual()) {
            return contentNode.asText("").trim();
        }
        if (contentNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : contentNode) {
                if (item == null || item.isNull()) {
                    continue;
                }
                String piece = "";
                if (item.isTextual()) {
                    piece = item.asText("");
                } else {
                    piece = item.path("text").asText("");
                    if (piece == null || piece.trim().isEmpty()) {
                        piece = item.path("content").asText("");
                    }
                }
                if (piece != null && !piece.trim().isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(piece.trim());
                }
            }
            return sb.toString().trim();
        }
        return "";
    }

    private List<Map<String, String>> buildAnswerMessages(String query, List<RetrievedDoc> docs, List<MemorySummaryDocument> memories) {
        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();

        Map<String, String> system = new HashMap<String, String>();
        system.put("role", "system");
        system.put("content", "你是企业知识库问答助手。请仅基于给定上下文回答，先给结论，再给依据；若上下文不足请明确说明，不要编造。");
        messages.add(system);

        StringBuilder user = new StringBuilder();
        user.append("问题：").append(query == null ? "" : query).append("\n\n");
        if (memories != null && !memories.isEmpty()) {
            user.append("历史记忆（同用户）：\n");
            for (int i = 0; i < memories.size() && i < 5; i++) {
                user.append(i + 1).append(". ").append(safeText(memories.get(i).getSummary(), 400)).append("\n");
            }
            user.append("\n");
        }
        user.append("检索上下文（同用户）：\n");
        for (int i = 0; i < docs.size() && i < 8; i++) {
            RetrievedDoc doc = docs.get(i);
            user.append("[")
                    .append(i + 1)
                    .append("] fileId=")
                    .append(doc.document().fileId())
                    .append(", chunkId=")
                    .append(doc.document().chunkId())
                    .append("\n")
                    .append(safeText(doc.document().text(), 1200))
                    .append("\n\n");
        }
        user.append("请输出一段面向用户的完整回答，不要输出JSON。");

        Map<String, String> userMsg = new HashMap<String, String>();
        userMsg.put("role", "user");
        userMsg.put("content", user.toString());
        messages.add(userMsg);
        return messages;
    }

    private String safeText(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
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
