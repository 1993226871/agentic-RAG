package com.agenticrag;

import com.agenticrag.infra.chunk.SimpleLangchain4jChunker;
import com.agenticrag.infra.embedding.MockAliEmbeddingClient;
import com.agenticrag.infra.es.InMemoryElasticsearchHybridStore;
import com.agenticrag.infra.inmemory.InMemoryMessageQueue;
import com.agenticrag.infra.inmemory.InMemoryMinioChunkStorage;
import com.agenticrag.infra.inmemory.InMemoryRedisBitmapStore;
import com.agenticrag.infra.rerank.MockBgeReranker;
import com.agenticrag.infra.tika.TikaTextParser;
import com.agenticrag.model.QaResult;
import com.agenticrag.service.AsyncIngestionConsumer;
import com.agenticrag.service.OfflineUploadService;
import com.agenticrag.service.OnlineQaService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class EndToEndRagFlowTest {
    @Test
    void shouldRunOfflineUploadAndOnlineQaFullFlow() {
        InMemoryRedisBitmapStore bitmap = new InMemoryRedisBitmapStore();
        InMemoryMinioChunkStorage minio = new InMemoryMinioChunkStorage();
        InMemoryMessageQueue mq = new InMemoryMessageQueue();
        InMemoryElasticsearchHybridStore store = new InMemoryElasticsearchHybridStore();
        MockAliEmbeddingClient embedding = new MockAliEmbeddingClient(8);

        OfflineUploadService uploadService = new OfflineUploadService(bitmap, minio, mq);
        AsyncIngestionConsumer consumer = new AsyncIngestionConsumer(
                mq, minio, new TikaTextParser(), new SimpleLangchain4jChunker(50, 10), embedding, store
        );
        OnlineQaService qaService = new OnlineQaService(embedding, store, new MockBgeReranker(), 60);

        String fileId = "file-rag-e2e";
        String content =
                "Apache Tika extracts text from files.\n"
                        + "LangChain4j style chunking creates retrieval units.\n"
                        + "Embedding vectors and BM25 together improve recall in RAG.\n";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        byte[][] chunks = split(bytes, 20);
        uploadService.initUpload(fileId, chunks.length);
        for (int i = 0; i < chunks.length; i++) {
            uploadService.uploadChunk(fileId, chunks.length, i, chunks[i]);
        }
        int inserted = consumer.consumeOnce();
        Assertions.assertTrue(inserted > 0);
        QaResult result = qaService.ask("RAG 中为什么要结合 BM25 与向量检索", 3);
        Assertions.assertFalse(result.contexts().isEmpty());
        Assertions.assertTrue(result.answer().contains("BM25") || result.answer().contains("向量"));
    }

    private static byte[][] split(byte[] source, int chunkSize) {
        int chunks = (source.length + chunkSize - 1) / chunkSize;
        byte[][] out = new byte[chunks][];
        for (int i = 0; i < chunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, source.length);
            out[i] = java.util.Arrays.copyOfRange(source, start, end);
        }
        return out;
    }
}
