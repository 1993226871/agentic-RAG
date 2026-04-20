package com.agenticrag.service;

import com.agenticrag.model.ChunkDocument;
import com.agenticrag.model.UploadCompleteMessage;
import com.agenticrag.ports.EmbeddingClient;
import com.agenticrag.ports.HybridSearchStore;
import com.agenticrag.ports.MessageQueue;
import com.agenticrag.ports.MinioChunkStorage;
import com.agenticrag.ports.TextChunker;
import com.agenticrag.ports.TikaParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AsyncIngestionConsumer {
    private static final Logger log = LoggerFactory.getLogger(AsyncIngestionConsumer.class);
    private final MessageQueue messageQueue;
    private final MinioChunkStorage minioChunkStorage;
    private final TikaParser tikaParser;
    private final TextChunker textChunker;
    private final EmbeddingClient embeddingClient;
    private final HybridSearchStore hybridSearchStore;

    public AsyncIngestionConsumer(
            MessageQueue messageQueue,
            MinioChunkStorage minioChunkStorage,
            TikaParser tikaParser,
            TextChunker textChunker,
            EmbeddingClient embeddingClient,
            HybridSearchStore hybridSearchStore
    ) {
        this.messageQueue = messageQueue;
        this.minioChunkStorage = minioChunkStorage;
        this.tikaParser = tikaParser;
        this.textChunker = textChunker;
        this.embeddingClient = embeddingClient;
        this.hybridSearchStore = hybridSearchStore;
    }

    public int consumeOnce() {
        UploadCompleteMessage message = messageQueue.poll();
        if (message == null) {
            return 0;
        }
        return consume(message);
    }

    public int consume(UploadCompleteMessage message) {
        log.info("Begin async ingestion for fileId={}, objectKey={}", message.fileId(), message.objectKey());
        byte[] mergedContent = minioChunkStorage.getMergedObject(message.objectKey());
        if (mergedContent == null) {
            throw new IllegalStateException("Merged object not found: " + message.objectKey());
        }
        String parsedText = tikaParser.parse(mergedContent);
        List<String> chunks = textChunker.chunk(parsedText);
        log.info("Parsed text and generated {} chunks for fileId={}", chunks.size(), message.fileId());
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            List<Double> vector = embeddingClient.embed(chunk);
            ChunkDocument doc = new ChunkDocument(message.fileId() + "-chunk-" + i, message.fileId(), chunk, vector);
            hybridSearchStore.save(doc);
        }
        log.info("Ingestion finished for fileId={}, insertedChunks={}", message.fileId(), chunks.size());
        return chunks.size();
    }
}
