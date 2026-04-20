package com.agenticrag.infra.inmemory;

import com.agenticrag.ports.RedisBitmapStore;
import com.agenticrag.model.UserUploadFile;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRedisBitmapStore implements RedisBitmapStore {
    private final Map<String, BitSet> bitmap = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalChunks = new ConcurrentHashMap<>();
    private final Map<String, String> fileNames = new ConcurrentHashMap<>();

    @Override
    public void init(String fileId, int totalChunks) {
        this.totalChunks.put(fileId, totalChunks);
        bitmap.putIfAbsent(fileId, new BitSet(totalChunks));
    }

    @Override
    public void markUploaded(String fileId, int chunkIndex) {
        bitmap.computeIfAbsent(fileId, ignored -> new BitSet()).set(chunkIndex);
    }

    @Override
    public void bindFileName(String fileId, String fileName) {
        if (fileId == null || fileId.trim().isEmpty() || fileName == null || fileName.trim().isEmpty()) {
            return;
        }
        fileNames.put(fileId, fileName.trim());
    }

    @Override
    public boolean isUploaded(String fileId, int chunkIndex) {
        BitSet bitSet = bitmap.get(fileId);
        return bitSet != null && bitSet.get(chunkIndex);
    }

    @Override
    public boolean allUploaded(String fileId) {
        BitSet bitSet = bitmap.get(fileId);
        Integer total = totalChunks.get(fileId);
        if (bitSet == null || total == null) {
            return false;
        }
        return bitSet.nextClearBit(0) >= total;
    }

    @Override
    public List<UserUploadFile> listUserFiles(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String prefix = userId.trim() + ":";
        List<UserUploadFile> out = new ArrayList<UserUploadFile>();
        for (Map.Entry<String, Integer> entry : totalChunks.entrySet()) {
            String fileId = entry.getKey();
            if (!fileId.startsWith(prefix)) {
                continue;
            }
            int total = entry.getValue() == null ? 0 : entry.getValue();
            BitSet bitSet = bitmap.get(fileId);
            int uploaded = bitSet == null ? 0 : bitSet.cardinality();
            String md5 = fileId.substring(prefix.length());
            String fileName = fileNames.get(fileId);
            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = md5;
            }
            out.add(new UserUploadFile(userId, fileId, md5, fileName, total, uploaded, total > 0 && uploaded >= total));
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
    public void deleteFile(String fileId) {
        bitmap.remove(fileId);
        totalChunks.remove(fileId);
        fileNames.remove(fileId);
    }
}
