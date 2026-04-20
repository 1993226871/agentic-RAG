package com.agenticrag.model;

public class UploadCompleteMessage {
    private String fileId;
    private String objectKey;

    public UploadCompleteMessage() {
    }

    public UploadCompleteMessage(String fileId, String objectKey) {
        this.fileId = fileId;
        this.objectKey = objectKey;
    }

    public String fileId() {
        return fileId;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String objectKey() {
        return objectKey;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }
}
