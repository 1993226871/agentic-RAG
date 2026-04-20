package com.agenticrag.ports;

public interface MinioChunkStorage {
    void putChunk(String fileId, int chunkIndex, byte[] content);

    byte[] mergeChunks(String fileId, int totalChunks);

    byte[] getMergedObject(String objectKey);
}
