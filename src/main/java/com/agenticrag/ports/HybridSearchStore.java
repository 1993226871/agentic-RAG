package com.agenticrag.ports;

import com.agenticrag.model.ChunkDocument;
import com.agenticrag.model.RetrievedDoc;

import java.util.List;

public interface HybridSearchStore {
    void save(ChunkDocument document);

    List<RetrievedDoc> knnSearch(List<Double> queryVector, int topK);

    List<RetrievedDoc> bm25Search(String query, int topK);

    int countByFileId(String fileId);

    void deleteByFileId(String fileId);
}
