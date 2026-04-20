package com.agenticrag;

import com.agenticrag.infra.chunk.SimpleLangchain4jChunker;
import com.agenticrag.infra.embedding.MockAliEmbeddingClient;
import com.agenticrag.infra.es.InMemoryElasticsearchHybridStore;
import com.agenticrag.infra.inmemory.InMemoryMessageQueue;
import com.agenticrag.infra.inmemory.InMemoryMinioChunkStorage;
import com.agenticrag.infra.tika.TikaTextParser;
import com.agenticrag.model.UploadCompleteMessage;
import com.agenticrag.service.AsyncIngestionConsumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class AsyncIngestionConsumerTest {
    @Test
    void shouldParseChunkEmbedAndStoreToHybridIndex() {
        InMemoryMessageQueue mq = new InMemoryMessageQueue();
        InMemoryMinioChunkStorage minio = new InMemoryMinioChunkStorage();
        InMemoryElasticsearchHybridStore store = new InMemoryElasticsearchHybridStore();

        String fileId = "file-rag-2";
        String objectKey = fileId + "/merged";
        String text = "Elasticsearch supports BM25. Vector search supports semantic retrieval. Hybrid search is strong.";
        minio.putChunk(fileId, 0, text.getBytes(StandardCharsets.UTF_8));
        minio.mergeChunks(fileId, 1);
        mq.publish(new UploadCompleteMessage(fileId, objectKey));

        AsyncIngestionConsumer consumer = new AsyncIngestionConsumer(
                mq,
                minio,
                new TikaTextParser(),
                new SimpleLangchain4jChunker(40, 10),
                new MockAliEmbeddingClient(8),
                store
        );
        int inserted = consumer.consumeOnce();
        Assertions.assertTrue(inserted >= 1);
        Assertions.assertEquals(inserted, store.allDocs().size());
        Assertions.assertFalse(store.allDocs().get(0).vector().isEmpty());
    }
}
