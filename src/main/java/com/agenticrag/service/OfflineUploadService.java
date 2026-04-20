package com.agenticrag.service;

import com.agenticrag.model.UploadCompleteMessage;
import com.agenticrag.ports.MessageQueue;
import com.agenticrag.ports.MinioChunkStorage;
import com.agenticrag.ports.RedisBitmapStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OfflineUploadService {
    private static final Logger log = LoggerFactory.getLogger(OfflineUploadService.class);
    private final RedisBitmapStore bitmapStore;
    private final MinioChunkStorage minioChunkStorage;
    private final MessageQueue messageQueue;

    public OfflineUploadService(RedisBitmapStore bitmapStore, MinioChunkStorage minioChunkStorage, MessageQueue messageQueue) {
        this.bitmapStore = bitmapStore;
        this.minioChunkStorage = minioChunkStorage;
        this.messageQueue = messageQueue;
    }

    public void initUpload(String fileId, int totalChunks) {
        bitmapStore.init(fileId, totalChunks);
    }

    public boolean uploadChunk(String fileId, int totalChunks, int chunkIndex, byte[] content) {
        if (bitmapStore.isUploaded(fileId, chunkIndex)) {
            return false;
        }
        minioChunkStorage.putChunk(fileId, chunkIndex, content);
        bitmapStore.markUploaded(fileId, chunkIndex);

        if (bitmapStore.allUploaded(fileId)) {
            log.info("All chunks uploaded for fileId={}, start merge and publish", fileId);
            minioChunkStorage.mergeChunks(fileId, totalChunks);
            String objectKey = fileId + "/merged";
            log.info("Merged object ready objectKey={}, publishing MQ message", objectKey);
            messageQueue.publish(new UploadCompleteMessage(fileId, objectKey));
            log.info("MQ message published for fileId={}", fileId);
        }
        return true;
    }
}
