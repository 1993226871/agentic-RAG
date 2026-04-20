package com.agenticrag.service;

import com.agenticrag.model.UploadCompleteMessage;
import com.agenticrag.model.UserUploadFile;
import com.agenticrag.ports.HybridSearchStore;
import com.agenticrag.ports.MessageQueue;
import com.agenticrag.ports.MinioChunkStorage;
import com.agenticrag.ports.RedisBitmapStore;
import com.agenticrag.ports.UploadFileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OfflineUploadService {
    private static final Logger log = LoggerFactory.getLogger(OfflineUploadService.class);
    private final RedisBitmapStore bitmapStore;
    private final MinioChunkStorage minioChunkStorage;
    private final MessageQueue messageQueue;
    private final UploadFileStore uploadFileStore;
    private final HybridSearchStore hybridSearchStore;
    private final AsyncIngestionConsumer asyncIngestionConsumer;

    @Autowired
    public OfflineUploadService(
            RedisBitmapStore bitmapStore,
            MinioChunkStorage minioChunkStorage,
            MessageQueue messageQueue,
            UploadFileStore uploadFileStore,
            HybridSearchStore hybridSearchStore,
            AsyncIngestionConsumer asyncIngestionConsumer
    ) {
        this.bitmapStore = bitmapStore;
        this.minioChunkStorage = minioChunkStorage;
        this.messageQueue = messageQueue;
        this.uploadFileStore = uploadFileStore;
        this.hybridSearchStore = hybridSearchStore;
        this.asyncIngestionConsumer = asyncIngestionConsumer;
    }

    public OfflineUploadService(
            RedisBitmapStore bitmapStore,
            MinioChunkStorage minioChunkStorage,
            MessageQueue messageQueue,
            UploadFileStore uploadFileStore,
            HybridSearchStore hybridSearchStore
    ) {
        this(bitmapStore, minioChunkStorage, messageQueue, uploadFileStore, hybridSearchStore, null);
    }

    public void initUpload(String fileId, int totalChunks) {
        initUpload(fileId, totalChunks, null);
    }

    public void initUpload(String fileId, int totalChunks, String fileName) {
        bitmapStore.init(fileId, totalChunks);
        bitmapStore.bindFileName(fileId, fileName);
        String userId = userIdFromFileId(fileId);
        String fileMd5 = md5FromFileId(fileId);
        uploadFileStore.upsert(userId, fileId, fileMd5, safeFileName(fileName, fileMd5));
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
            UploadCompleteMessage message = new UploadCompleteMessage(fileId, objectKey);
            try {
                messageQueue.publish(message);
                log.info("MQ message published for fileId={}", fileId);
            } catch (Exception ex) {
                log.error("MQ publish failed for fileId={}, fallback to direct ingestion", fileId, ex);
                if (asyncIngestionConsumer != null) {
                    int inserted = asyncIngestionConsumer.consume(message);
                    log.info("Fallback ingestion finished for fileId={}, insertedChunks={}", fileId, inserted);
                } else {
                    throw ex;
                }
            }
        }
        return true;
    }

    public List<UserUploadFile> listUserFiles(String userId) {
        List<UserUploadFile> files = uploadFileStore.listByUser(userId);
        for (UserUploadFile file : files) {
            int chunkCount = hybridSearchStore.countByFileId(file.getFileId());
            file.setCompleted(chunkCount > 0);
            file.setUploadedChunks(chunkCount);
            file.setTotalChunks(chunkCount);
        }
        return files;
    }

    public boolean deleteUserFile(String userId, String fileId) {
        if (!uploadFileStore.existsByUserAndFileId(userId, fileId)) {
            return false;
        }
        uploadFileStore.deleteByFileId(fileId);
        hybridSearchStore.deleteByFileId(fileId);
        minioChunkStorage.deleteByFileId(fileId);
        bitmapStore.deleteFile(fileId);
        return true;
    }

    private String userIdFromFileId(String fileId) {
        if (fileId == null) {
            return "";
        }
        int idx = fileId.indexOf(':');
        if (idx <= 0) {
            return "";
        }
        return fileId.substring(0, idx);
    }

    private String md5FromFileId(String fileId) {
        if (fileId == null) {
            return "";
        }
        int idx = fileId.indexOf(':');
        if (idx < 0 || idx + 1 >= fileId.length()) {
            return fileId;
        }
        return fileId.substring(idx + 1);
    }

    private String safeFileName(String fileName, String fallback) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return fallback == null ? "" : fallback;
        }
        return fileName.trim();
    }
}
