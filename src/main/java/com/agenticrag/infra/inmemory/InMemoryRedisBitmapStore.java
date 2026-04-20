package com.agenticrag.infra.inmemory;

import com.agenticrag.ports.RedisBitmapStore;

import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRedisBitmapStore implements RedisBitmapStore {
    private final Map<String, BitSet> bitmap = new ConcurrentHashMap<>();
    private final Map<String, Integer> totalChunks = new ConcurrentHashMap<>();

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
}
