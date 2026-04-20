package com.agenticrag.model;

import java.util.List;

public class ChunkDocument {
    private String chunkId;
    private String fileId;
    private String text;
    private List<Double> vector;

    public ChunkDocument() {
    }

    public ChunkDocument(String chunkId, String fileId, String text, List<Double> vector) {
        this.chunkId = chunkId;
        this.fileId = fileId;
        this.text = text;
        this.vector = vector;
    }

    public String chunkId() {
        return chunkId;
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
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

    public String text() {
        return text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<Double> vector() {
        return vector;
    }

    public List<Double> getVector() {
        return vector;
    }

    public void setVector(List<Double> vector) {
        this.vector = vector;
    }
}
