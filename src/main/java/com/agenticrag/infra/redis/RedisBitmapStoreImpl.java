package com.agenticrag.infra.redis;

import com.agenticrag.ports.RedisBitmapStore;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisBitmapStoreImpl implements RedisBitmapStore {
    private final StringRedisTemplate redisTemplate;

    public RedisBitmapStoreImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void init(String fileId, int totalChunks) {
        redisTemplate.delete(bitmapKey(fileId));
        redisTemplate.opsForValue().set(totalKey(fileId), String.valueOf(totalChunks));
    }

    @Override
    public void markUploaded(String fileId, int chunkIndex) {
        redisTemplate.opsForValue().setBit(bitmapKey(fileId), chunkIndex, true);
    }

    @Override
    public boolean isUploaded(String fileId, int chunkIndex) {
        Boolean value = redisTemplate.opsForValue().getBit(bitmapKey(fileId), chunkIndex);
        return value != null && value;
    }

    @Override
    public boolean allUploaded(String fileId) {
        String totalString = redisTemplate.opsForValue().get(totalKey(fileId));
        if (totalString == null) {
            return false;
        }
        int total = Integer.parseInt(totalString);
        Long count = redisTemplate.execute(new RedisCallback<Long>() {
            @Override
            public Long doInRedis(org.springframework.data.redis.connection.RedisConnection connection) {
                return connection.stringCommands().bitCount(bitmapKey(fileId).getBytes());
            }
        });
        return count != null && count >= total;
    }

    private String bitmapKey(String fileId) {
        return "rag:upload:bitmap:" + fileId;
    }

    private String totalKey(String fileId) {
        return "rag:upload:total:" + fileId;
    }
}
