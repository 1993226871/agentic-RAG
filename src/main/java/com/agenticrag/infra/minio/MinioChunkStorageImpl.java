package com.agenticrag.infra.minio;

import com.agenticrag.ports.MinioChunkStorage;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MinioChunkStorageImpl implements MinioChunkStorage {
    private final MinioClient minioClient;
    private final String bucket;

    public MinioChunkStorageImpl(MinioClient minioClient, String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        ensureBucket();
    }

    @Override
    public void putChunk(String fileId, int chunkIndex, byte[] content) {
        ensureBucket();
        String object = fileId + "/chunks/" + chunkIndex;
        putObject(object, content);
    }

    @Override
    public byte[] mergeChunks(String fileId, int totalChunks) {
        ensureBucket();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = 0; i < totalChunks; i++) {
            String object = fileId + "/chunks/" + i;
            byte[] bytes = readObject(object);
            if (bytes == null) {
                throw new IllegalStateException("Missing chunk in MinIO: " + object);
            }
            output.write(bytes, 0, bytes.length);
        }
        byte[] merged = output.toByteArray();
        putObject(fileId + "/merged", merged);
        return merged;
    }

    @Override
    public byte[] getMergedObject(String objectKey) {
        ensureBucket();
        return readObject(objectKey);
    }

    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MinIO bucket", e);
        }
    }

    private void putObject(String object, byte[] content) {
        try {
            ByteArrayInputStream input = new ByteArrayInputStream(content);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(object)
                            .stream(input, content.length, -1)
                            .contentType("application/octet-stream")
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to put object to MinIO: " + object, e);
        }
    }

    private byte[] readObject(String object) {
        try (InputStream input = minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(object).build())) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
