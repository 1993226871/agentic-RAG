package com.agenticrag.infra.es;

import com.agenticrag.config.RagProperties;
import com.agenticrag.model.UserUploadFile;
import com.agenticrag.ports.UploadFileStore;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElasticsearchUploadFileStore implements UploadFileStore {
    private final RestHighLevelClient client;
    private final String indexName;

    public ElasticsearchUploadFileStore(RestHighLevelClient client, RagProperties properties) {
        this.client = client;
        this.indexName = properties.getEs().getFileIndex();
        ensureIndex();
    }

    @Override
    public void upsert(String userId, String fileId, String fileMd5, String fileName) {
        Map<String, Object> source = new HashMap<String, Object>();
        source.put("userId", userId);
        source.put("fileId", fileId);
        source.put("fileMd5", fileMd5);
        source.put("fileName", fileName);
        source.put("updatedAt", System.currentTimeMillis());
        try {
            IndexRequest request = new IndexRequest(indexName).id(fileId).source(source);
            request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            client.index(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upsert upload file metadata", e);
        }
    }

    @Override
    public List<UserUploadFile> listByUser(String userId) {
        try {
            SearchSourceBuilder source = new SearchSourceBuilder();
            source.query(QueryBuilders.termQuery("userId", userId));
            source.size(1000);
            source.sort("updatedAt", SortOrder.DESC);
            SearchResponse response = client.search(new SearchRequest(indexName).source(source), RequestOptions.DEFAULT);
            List<UserUploadFile> out = new ArrayList<UserUploadFile>();
            for (SearchHit hit : response.getHits().getHits()) {
                Map<String, Object> map = hit.getSourceAsMap();
                UserUploadFile file = new UserUploadFile();
                file.setUserId(valueOf(map.get("userId")));
                file.setFileId(valueOf(map.get("fileId")));
                file.setFileMd5(valueOf(map.get("fileMd5")));
                file.setFileName(valueOf(map.get("fileName")));
                out.add(file);
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list user upload files", e);
        }
    }

    @Override
    public boolean existsByUserAndFileId(String userId, String fileId) {
        try {
            SearchSourceBuilder source = new SearchSourceBuilder();
            source.query(QueryBuilders.boolQuery()
                    .must(QueryBuilders.termQuery("userId", userId))
                    .must(QueryBuilders.termQuery("fileId", fileId)));
            source.size(1);
            SearchResponse response = client.search(new SearchRequest(indexName).source(source), RequestOptions.DEFAULT);
            return response.getHits().getHits().length > 0;
        } catch (Exception e) {
            throw new RuntimeException("Failed to check upload file ownership", e);
        }
    }

    @Override
    public void deleteByFileId(String fileId) {
        try {
            DeleteRequest request = new DeleteRequest(indexName, fileId);
            request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            client.delete(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete upload file metadata", e);
        }
    }

    private String valueOf(Object value) {
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
            Map<String, Object> props = new HashMap<String, Object>();
            props.put("userId", type("keyword"));
            props.put("fileId", type("keyword"));
            props.put("fileMd5", type("keyword"));
            props.put("fileName", type("keyword"));
            props.put("updatedAt", type("long"));
            Map<String, Object> mapping = new HashMap<String, Object>();
            mapping.put("properties", props);
            request.mapping(mapping);
            client.indices().create(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create upload-file index", e);
        }
    }

    private Map<String, Object> type(String type) {
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("type", type);
        return out;
    }
}
