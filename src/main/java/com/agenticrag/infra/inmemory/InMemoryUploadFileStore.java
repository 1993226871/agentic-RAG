package com.agenticrag.infra.inmemory;

import com.agenticrag.model.UserUploadFile;
import com.agenticrag.ports.UploadFileStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryUploadFileStore implements UploadFileStore {
    private final Map<String, UserUploadFile> files = new ConcurrentHashMap<String, UserUploadFile>();

    @Override
    public void upsert(String userId, String fileId, String fileMd5, String fileName) {
        UserUploadFile old = files.get(fileId);
        UserUploadFile doc = old == null ? new UserUploadFile() : old;
        doc.setUserId(userId);
        doc.setFileId(fileId);
        doc.setFileMd5(fileMd5);
        doc.setFileName(fileName);
        files.put(fileId, doc);
    }

    @Override
    public List<UserUploadFile> listByUser(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<UserUploadFile> out = new ArrayList<UserUploadFile>();
        for (UserUploadFile value : files.values()) {
            if (userId.equals(value.getUserId())) {
                UserUploadFile copy = new UserUploadFile();
                copy.setUserId(value.getUserId());
                copy.setFileId(value.getFileId());
                copy.setFileMd5(value.getFileMd5());
                copy.setFileName(value.getFileName());
                out.add(copy);
            }
        }
        out.sort(new Comparator<UserUploadFile>() {
            @Override
            public int compare(UserUploadFile a, UserUploadFile b) {
                return b.getFileId().compareTo(a.getFileId());
            }
        });
        return out;
    }

    @Override
    public boolean existsByUserAndFileId(String userId, String fileId) {
        UserUploadFile file = files.get(fileId);
        return file != null && userId != null && userId.equals(file.getUserId());
    }

    @Override
    public void deleteByFileId(String fileId) {
        files.remove(fileId);
    }
}
