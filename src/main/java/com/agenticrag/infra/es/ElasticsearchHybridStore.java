package com.agenticrag.infra.es;

import com.agenticrag.config.RagProperties;
import com.agenticrag.model.ChunkDocument;
import com.agenticrag.model.RetrievedDoc;
import com.agenticrag.ports.HybridSearchStore;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElasticsearchHybridStore implements HybridSearchStore {
    private final RestHighLevelClient client;
    private final String indexName;
    private final int vectorDims;

    public ElasticsearchHybridStore(RestHighLevelClient client, RagProperties properties) {
        this.client = client;
        this.indexName = properties.getEs().getIndex();
        this.vectorDims = properties.getEs().getVectorDims();
        ensureIndex();
    }

    @Override
    public void save(ChunkDocument document) {
        Map<String, Object> source = new HashMap<String, Object>();
        source.put("chunkId", document.chunkId());
        source.put("fileId", document.fileId());
        source.put("text", document.text());
        source.put("vector", document.vector());
        IndexRequest request = new IndexRequest(indexName).id(document.chunkId()).source(source);
        try {
            client.index(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException("Failed to index document to Elasticsearch", e);
        }
    }

    @Override
    public List<RetrievedDoc> knnSearch(List<Double> queryVector, int topK) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.size(topK);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("qv", queryVector);
        Script script = new Script(
                Script.DEFAULT_SCRIPT_TYPE,
                "painless",
                "cosineSimilarity(params.qv, 'vector') + 1.0",
                params
        );
        QueryBuilder query = QueryBuilders.scriptScoreQuery(QueryBuilders.matchAllQuery(), script);
        sourceBuilder.query(query);

        SearchRequest request = new SearchRequest(indexName);
        request.source(sourceBuilder);
        return execute(request);
    }

    @Override
    public List<RetrievedDoc> bm25Search(String query, int topK) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.size(topK);
        MatchQueryBuilder match = QueryBuilders.matchQuery("text", query);
        sourceBuilder.query(match);
        SearchRequest request = new SearchRequest(indexName);
        request.source(sourceBuilder);
        return execute(request);
    }

    @Override
    public int countByFileId(String fileId) {
        try {
            CountRequest request = new CountRequest(indexName);
            request.query(QueryBuilders.termQuery("fileId", fileId));
            CountResponse response = client.count(request, RequestOptions.DEFAULT);
            return (int) response.getCount();
        } catch (Exception e) {
            throw new RuntimeException("Elasticsearch count query failed", e);
        }
    }

    private List<RetrievedDoc> execute(SearchRequest request) {
        try {
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            List<RetrievedDoc> results = new ArrayList<RetrievedDoc>();
            for (SearchHit hit : response.getHits().getHits()) {
                Map<String, Object> source = hit.getSourceAsMap();
                ChunkDocument document = mapDocument(source);
                results.add(new RetrievedDoc(document, hit.getScore()));
            }
            return results;
        } catch (Exception e) {
            throw new RuntimeException("Elasticsearch query failed", e);
        }
    }

    private ChunkDocument mapDocument(Map<String, Object> source) {
        String chunkId = stringValue(source.get("chunkId"));
        String fileId = stringValue(source.get("fileId"));
        String text = stringValue(source.get("text"));
        List<Double> vector = new ArrayList<Double>();
        Object vec = source.get("vector");
        if (vec instanceof List) {
            List<?> raw = (List<?>) vec;
            for (Object item : raw) {
                if (item instanceof Number) {
                    vector.add(((Number) item).doubleValue());
                }
            }
        }
        return new ChunkDocument(chunkId, fileId, text, vector);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void ensureIndex() {
        try {
            boolean exists = client.indices().exists(new org.elasticsearch.client.indices.GetIndexRequest(indexName), RequestOptions.DEFAULT);
            if (exists) {
                return;
            }
            CreateIndexRequest request = new CreateIndexRequest(indexName);
            request.settings(Settings.builder().put("index.number_of_shards", 1).put("index.number_of_replicas", 0));
            Map<String, Object> vector = new HashMap<String, Object>();
            vector.put("type", "dense_vector");
            vector.put("dims", vectorDims);
            Map<String, Object> text = mapType("text");
            text.put("analyzer", "ik_max_word");
            text.put("search_analyzer", "ik_smart");
            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("chunkId", mapType("keyword"));
            properties.put("fileId", mapType("keyword"));
            properties.put("text", text);
            properties.put("vector", vector);
            Map<String, Object> mapping = new HashMap<String, Object>();
            mapping.put("properties", properties);
            request.mapping(mapping);
            client.indices().create(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Elasticsearch index, please ensure IK analyzer plugin is installed", e);
        }
    }

    private Map<String, Object> mapType(String type) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("type", type);
        return map;
    }
}
