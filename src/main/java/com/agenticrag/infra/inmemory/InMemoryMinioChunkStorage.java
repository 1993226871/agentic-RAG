package com.agenticrag.infra.inmemory;

import com.agenticrag.ports.MinioChunkStorage;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMinioChunkStorage implements MinioChunkStorage {
    private final Map<String, Map<Integer, byte[]>> chunkBucket = new ConcurrentHashMap<>();
    private final Map<String, byte[]> mergedBucket = new ConcurrentHashMap<>();

    @Override
    public void putChunk(String fileId, int chunkIndex, byte[] content) {
        chunkBucket
                .computeIfAbsent(fileId, ignored -> new ConcurrentHashMap<>())
                .put(chunkIndex, content);
    }

    @Override
    public byte[] mergeChunks(String fileId, int totalChunks) {
        Map<Integer, byte[]> chunks = chunkBucket.get(fileId);
        if (chunks == null || chunks.size() < totalChunks) {
            throw new IllegalStateException("Chunks are incomplete, cannot merge");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = 0; i < totalChunks; i++) {
            byte[] chunk = chunks.get(i);
            if (chunk == null) {
                throw new IllegalStateException("Missing chunk index: " + i);
            }
            output.write(chunk, 0, chunk.length);
        }
        byte[] merged = output.toByteArray();
        mergedBucket.put(mergedKey(fileId), merged);
        return merged;
    }

    @Override
    public byte[] getMergedObject(String objectKey) {
        return mergedBucket.get(objectKey);
    }

    @Override
    public void deleteByFileId(String fileId) {
        chunkBucket.remove(fileId);
        mergedBucket.remove(mergedKey(fileId));
    }

    public static String mergedKey(String fileId) {
        return fileId + "/merged";
    }
}
