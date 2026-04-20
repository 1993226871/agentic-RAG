package com.agenticrag;

import com.agenticrag.config.RagProperties;
import com.agenticrag.infra.embedding.AliyunEmbeddingClient;
import com.agenticrag.infra.rerank.BgeHttpReranker;
import com.agenticrag.model.ChunkDocument;
import com.agenticrag.model.RetrievedDoc;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

class RealAdapterFallbackTest {
    @Test
    void aliyunEmbeddingShouldFallbackWithoutApiKey() {
        RagProperties properties = new RagProperties();
        properties.getEs().setVectorDims(8);
        properties.getEmbedding().setApiKey("");
        properties.getEmbedding().setEndpoint("");
        AliyunEmbeddingClient client = new AliyunEmbeddingClient(properties, new RestTemplate());
        List<Double> vector = client.embed("测试 embedding 回退");
        Assertions.assertEquals(8, vector.size());
    }

    @Test
    void bgeRerankerShouldFallbackWithoutEndpoint() {
        RagProperties properties = new RagProperties();
        properties.getRerank().setEndpoint("");
        BgeHttpReranker reranker = new BgeHttpReranker(properties, new RestTemplate());

        List<RetrievedDoc> input = Arrays.asList(
                new RetrievedDoc(new ChunkDocument("c1", "f1", "redis bitmap for resume upload", Arrays.asList(0.1, 0.2)), 0.1),
                new RetrievedDoc(new ChunkDocument("c2", "f1", "rocketmq async processing pipeline", Arrays.asList(0.2, 0.3)), 0.09)
        );
        List<RetrievedDoc> output = reranker.rerank("resume upload", input, 1);
        Assertions.assertEquals(1, output.size());
    }
}
