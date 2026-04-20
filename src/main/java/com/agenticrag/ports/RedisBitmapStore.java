package com.agenticrag.ports;

import com.agenticrag.model.UserUploadFile;

import java.util.List;

public interface RedisBitmapStore {
    void init(String fileId, int totalChunks);

    void bindFileName(String fileId, String fileName);

    void markUploaded(String fileId, int chunkIndex);

    boolean isUploaded(String fileId, int chunkIndex);

    boolean allUploaded(String fileId);

    List<UserUploadFile> listUserFiles(String userId);

    void deleteFile(String fileId);
}
