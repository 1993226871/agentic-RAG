package com.agenticrag.api.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

public class InitUploadRequest {
    @NotBlank
    private String fileId;

    @Min(1)
    private int totalChunks;

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }
}
