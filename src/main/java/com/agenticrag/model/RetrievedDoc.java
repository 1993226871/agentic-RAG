package com.agenticrag.model;

public class RetrievedDoc {
    private ChunkDocument document;
    private double score;

    public RetrievedDoc() {
    }

    public RetrievedDoc(ChunkDocument document, double score) {
        this.document = document;
        this.score = score;
    }

    public ChunkDocument document() {
        return document;
    }

    public ChunkDocument getDocument() {
        return document;
    }

    public void setDocument(ChunkDocument document) {
        this.document = document;
    }

    public double score() {
        return score;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
