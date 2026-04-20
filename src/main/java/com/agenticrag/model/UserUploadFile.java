package com.agenticrag.model;

public class UserUploadFile {
    private String userId;
    private String fileId;
    private String fileMd5;
    private String fileName;
    private int totalChunks;
    private int uploadedChunks;
    private boolean completed;

    public UserUploadFile() {
    }

    public UserUploadFile(String userId, String fileId, String fileMd5, String fileName, int totalChunks, int uploadedChunks, boolean completed) {
        this.userId = userId;
        this.fileId = fileId;
        this.fileMd5 = fileMd5;
        this.fileName = fileName;
        this.totalChunks = totalChunks;
        this.uploadedChunks = uploadedChunks;
        this.completed = completed;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFileMd5() {
        return fileMd5;
    }

    public void setFileMd5(String fileMd5) {
        this.fileMd5 = fileMd5;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public int getUploadedChunks() {
        return uploadedChunks;
    }

    public void setUploadedChunks(int uploadedChunks) {
        this.uploadedChunks = uploadedChunks;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}
