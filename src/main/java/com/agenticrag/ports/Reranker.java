package com.agenticrag.ports;

import com.agenticrag.model.RetrievedDoc;

import java.util.List;

public interface Reranker {
    List<RetrievedDoc> rerank(String query, List<RetrievedDoc> candidates, int topK);
}
