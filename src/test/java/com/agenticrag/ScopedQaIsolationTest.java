package com.agenticrag;

import com.agenticrag.infra.embedding.MockAliEmbeddingClient;
import com.agenticrag.infra.es.InMemoryElasticsearchHybridStore;
import com.agenticrag.infra.rerank.MockBgeReranker;
import com.agenticrag.model.ChunkDocument;
import com.agenticrag.model.QaResult;
import com.agenticrag.service.OnlineQaService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

class ScopedQaIsolationTest {
    @Test
    void shouldOnlyReturnDocsInSpecifiedFileId() {
        MockAliEmbeddingClient embedding = new MockAliEmbeddingClient(8);
        InMemoryElasticsearchHybridStore store = new InMemoryElasticsearchHybridStore();

        store.save(new ChunkDocument("a1", "root:md5aaa", "Redis bitmap supports resume upload", embedding.embed("Redis bitmap supports resume upload")));
        store.save(new ChunkDocument("a2", "root:md5aaa", "BM25 and vector can use RRF fusion", embedding.embed("BM25 and vector can use RRF fusion")));
        store.save(new ChunkDocument("b1", "other:md5bbb", "Totally different private data", embedding.embed("Totally different private data")));

        OnlineQaService qaService = new OnlineQaService(embedding, store, new MockBgeReranker(), 60);
        QaResult result = qaService.askScoped("RRF BM25", 3, "root:md5aaa");

        Assertions.assertFalse(result.contexts().isEmpty());
        List<String> fileIds = result.contexts().stream().map(r -> r.document().fileId()).distinct().collect(Collectors.toList());
        Assertions.assertEquals(1, fileIds.size());
        Assertions.assertEquals("root:md5aaa", fileIds.get(0));
    }
}
