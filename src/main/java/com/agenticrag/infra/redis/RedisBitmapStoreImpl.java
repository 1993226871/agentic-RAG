package com.agenticrag.infra.redis;

import com.agenticrag.model.UserUploadFile;
import com.agenticrag.ports.RedisBitmapStore;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

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
    public void bindFileName(String fileId, String fileName) {
        if (fileId == null || fileId.trim().isEmpty() || fileName == null || fileName.trim().isEmpty()) {
            return;
        }
        redisTemplate.opsForValue().set(nameKey(fileId), fileName.trim());
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

    @Override
    public List<UserUploadFile> listUserFiles(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String prefix = userId.trim() + ":";
        Set<String> totalKeys = redisTemplate.keys(totalKey(prefix + "*"));
        if (totalKeys == null || totalKeys.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserUploadFile> out = new ArrayList<UserUploadFile>();
        String totalPrefix = "rag:upload:total:";
        for (String key : totalKeys) {
            if (key == null || !key.startsWith(totalPrefix)) {
                continue;
            }
            String fileId = key.substring(totalPrefix.length());
            String totalString = redisTemplate.opsForValue().get(key);
            int total = 0;
            try {
                total = Integer.parseInt(totalString == null ? "0" : totalString);
            } catch (NumberFormatException ignore) {
                total = 0;
            }
            Long uploadedCount = redisTemplate.execute(new RedisCallback<Long>() {
                @Override
                public Long doInRedis(org.springframework.data.redis.connection.RedisConnection connection) {
                    return connection.stringCommands().bitCount(bitmapKey(fileId).getBytes(StandardCharsets.UTF_8));
                }
            });
            int uploaded = uploadedCount == null ? 0 : uploadedCount.intValue();
            String md5 = fileId.startsWith(prefix) ? fileId.substring(prefix.length()) : fileId;
            String fileName = redisTemplate.opsForValue().get(nameKey(fileId));
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
        redisTemplate.delete(bitmapKey(fileId));
        redisTemplate.delete(totalKey(fileId));
        redisTemplate.delete(nameKey(fileId));
    }

    private String bitmapKey(String fileId) {
        return "rag:upload:bitmap:" + fileId;
    }

    private String totalKey(String fileId) {
        return "rag:upload:total:" + fileId;
    }

    private String nameKey(String fileId) {
        return "rag:upload:name:" + fileId;
    }
}
