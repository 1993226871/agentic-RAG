package com.agenticrag;

import com.agenticrag.infra.embedding.MockAliEmbeddingClient;
import com.agenticrag.infra.es.InMemoryElasticsearchHybridStore;
import com.agenticrag.infra.memory.InMemoryConversationMemoryStore;
import com.agenticrag.infra.memory.MockConversationSummarizer;
import com.agenticrag.infra.rerank.MockBgeReranker;
import com.agenticrag.model.MemorySummaryDocument;
import com.agenticrag.model.ChunkDocument;
import com.agenticrag.model.QaResult;
import com.agenticrag.ports.QueryRewriter;
import com.agenticrag.service.OnlineQaService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

class OnlineQaServiceTest {
    @Test
    void shouldUseKnnAndBm25ThenRrfAndRerank() {
        MockAliEmbeddingClient embedding = new MockAliEmbeddingClient(8);
        InMemoryElasticsearchHybridStore store = new InMemoryElasticsearchHybridStore();

        store.save(new ChunkDocument(
                "c1",
                "f1",
                "Redis bitmap can track upload chunk progress and support resume upload.",
                embedding.embed("Redis bitmap can track upload chunk progress and support resume upload.")
        ));
        store.save(new ChunkDocument(
                "c2",
                "f1",
                "RocketMQ sends async event after merge for parsing pipeline.",
                embedding.embed("RocketMQ sends async event after merge for parsing pipeline.")
        ));
        store.save(new ChunkDocument(
                "c3",
                "f2",
                "Unrelated content about weather forecast.",
                embedding.embed("Unrelated content about weather forecast.")
        ));

        OnlineQaService qa = new OnlineQaService(embedding, store, new MockBgeReranker(), 60);
        QaResult result = qa.ask("如何实现分片上传的断点续传", 2);

        Assertions.assertEquals(3, result.contexts().size());
        List<String> topIds = result.contexts().stream().map(r -> r.document().chunkId()).collect(Collectors.toList());
        Assertions.assertTrue(topIds.contains("c1"));
        Assertions.assertFalse(result.answer().trim().isEmpty());
    }

    @Test
    void shouldUseRewrittenQueriesForMergedRetrieval() {
        MockAliEmbeddingClient embedding = new MockAliEmbeddingClient(8);
        InMemoryElasticsearchHybridStore store = new InMemoryElasticsearchHybridStore();
        InMemoryConversationMemoryStore memoryStore = new InMemoryConversationMemoryStore();

        store.save(new ChunkDocument(
                "rw-1",
                "f-rewrite",
                "向量召回和关键词召回可以做混合检索。",
                embedding.embed("向量召回和关键词召回可以做混合检索。")
        ));

        QueryRewriter fixedRewriter = (query, variants) -> Arrays.asList("混合检索 如何做");
        OnlineQaService qa = new OnlineQaService(
                embedding,
                store,
                new MockBgeReranker(),
                fixedRewriter,
                memoryStore,
                new MockConversationSummarizer(),
                60,
                3,
                3,
                20
        );
        QaResult result = qa.askScoped("多路召回", 1, "f-rewrite", "root", "s1");

        Assertions.assertFalse(result.contexts().isEmpty());
        Assertions.assertEquals("rw-1", result.contexts().get(0).document().chunkId());
    }

    @Test
    void shouldSummarizeWhenSessionEnded() {
        MockAliEmbeddingClient embedding = new MockAliEmbeddingClient(8);
        InMemoryElasticsearchHybridStore store = new InMemoryElasticsearchHybridStore();
        InMemoryConversationMemoryStore memoryStore = new InMemoryConversationMemoryStore();

        store.save(new ChunkDocument("m1", "root:f1", "RAG 可先检索再生成。", embedding.embed("RAG 可先检索再生成。")));

        OnlineQaService qa = new OnlineQaService(
                embedding,
                store,
                new MockBgeReranker(),
                (query, variants) -> Arrays.asList("RAG 检索 生成"),
                memoryStore,
                new MockConversationSummarizer(),
                60,
                3,
                3,
                20
        );
        qa.askScoped("什么是RAG", 1, "root:f1", "root", "session-end");
        boolean summarized = qa.endSession("root", "session-end");

        Assertions.assertTrue(summarized);
        List<MemorySummaryDocument> hit = memoryStore.search("root", "RAG", 3);
        Assertions.assertFalse(hit.isEmpty());
    }
}
