package com.agenticrag.ports;

import com.agenticrag.model.UserUploadFile;

import java.util.List;

public interface UploadFileStore {
    void upsert(String userId, String fileId, String fileMd5, String fileName);

    List<UserUploadFile> listByUser(String userId);

    boolean existsByUserAndFileId(String userId, String fileId);

    void deleteByFileId(String fileId);
}
