package com.agenticrag;

import com.agenticrag.infra.inmemory.InMemoryMessageQueue;
import com.agenticrag.infra.inmemory.InMemoryMinioChunkStorage;
import com.agenticrag.infra.inmemory.InMemoryRedisBitmapStore;
import com.agenticrag.model.UploadCompleteMessage;
import com.agenticrag.service.OfflineUploadService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class OfflineUploadServiceTest {
    @Test
    void shouldSupportResumeAndMergeWhenAllChunksUploaded() {
        InMemoryRedisBitmapStore bitmap = new InMemoryRedisBitmapStore();
        InMemoryMinioChunkStorage minio = new InMemoryMinioChunkStorage();
        InMemoryMessageQueue mq = new InMemoryMessageQueue();
        OfflineUploadService service = new OfflineUploadService(bitmap, minio, mq);

        String fileId = "file-rag-1";
        String content = "RAG allows combining retrieval and generation for accurate answers.";
        byte[] raw = content.getBytes(StandardCharsets.UTF_8);
        byte[][] chunks = new byte[][]{
                java.util.Arrays.copyOfRange(raw, 0, raw.length / 3),
                java.util.Arrays.copyOfRange(raw, raw.length / 3, raw.length * 2 / 3),
                java.util.Arrays.copyOfRange(raw, raw.length * 2 / 3, raw.length)
        };
        service.initUpload(fileId, chunks.length);

        service.uploadChunk(fileId, chunks.length, 0, chunks[0]);
        service.uploadChunk(fileId, chunks.length, 2, chunks[2]);
        Assertions.assertTrue(bitmap.isUploaded(fileId, 0));
        Assertions.assertTrue(bitmap.isUploaded(fileId, 2));
        Assertions.assertFalse(bitmap.isUploaded(fileId, 1));
        Assertions.assertNull(mq.poll(), "未完成时不应发送消息");

        service.uploadChunk(fileId, chunks.length, 1, chunks[1]);
        UploadCompleteMessage message = mq.poll();
        Assertions.assertNotNull(message);
        Assertions.assertEquals(fileId, message.fileId());
        Assertions.assertEquals(fileId + "/merged", message.objectKey());
        Assertions.assertArrayEquals(content.getBytes(StandardCharsets.UTF_8), minio.getMergedObject(message.objectKey()));
    }
}
