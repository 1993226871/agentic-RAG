package com.agenticrag.ports;

public interface RedisBitmapStore {
    void init(String fileId, int totalChunks);

    void markUploaded(String fileId, int chunkIndex);

    boolean isUploaded(String fileId, int chunkIndex);

    boolean allUploaded(String fileId);
}
