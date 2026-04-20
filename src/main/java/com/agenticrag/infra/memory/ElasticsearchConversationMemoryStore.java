package com.agenticrag.infra.memory;

import com.agenticrag.config.RagProperties;
import com.agenticrag.model.MemorySummaryDocument;
import com.agenticrag.ports.ConversationMemoryStore;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElasticsearchConversationMemoryStore implements ConversationMemoryStore {
    private final RestHighLevelClient client;
    private final String indexName;

    public ElasticsearchConversationMemoryStore(RestHighLevelClient client, RagProperties properties) {
        this.client = client;
        this.indexName = properties.getMemory().getIndex();
        ensureIndex();
    }

    @Override
    public void save(MemorySummaryDocument document) {
        try {
            Map<String, Object> source = new HashMap<String, Object>();
            source.put("id", document.getId());
            source.put("userId", document.getUserId());
            source.put("sessionId", document.getSessionId());
            source.put("summary", document.getSummary());
            source.put("rawConversation", document.getRawConversation());
            source.put("createdAt", document.getCreatedAt());
            client.index(new IndexRequest(indexName).id(document.getId()).source(source), RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save memory summary", e);
        }
    }

    @Override
    public List<MemorySummaryDocument> search(String userId, String query, int topK) {
        try {
            SearchSourceBuilder builder = new SearchSourceBuilder();
            builder.size(topK);
            BoolQueryBuilder bool = QueryBuilders.boolQuery()
                    .must(QueryBuilders.termQuery("userId", userId))
                    .should(QueryBuilders.matchQuery("summary", query))
                    .should(QueryBuilders.matchQuery("rawConversation", query))
                    .minimumShouldMatch(1);
            builder.query(bool);
            SearchRequest request = new SearchRequest(indexName).source(builder);
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            List<MemorySummaryDocument> out = new ArrayList<MemorySummaryDocument>();
            for (SearchHit hit : response.getHits().getHits()) {
                out.add(map(hit.getSourceAsMap()));
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Failed to query memory summaries", e);
        }
    }

    private MemorySummaryDocument map(Map<String, Object> source) {
        MemorySummaryDocument doc = new MemorySummaryDocument();
        doc.setId(String.valueOf(source.get("id")));
        doc.setUserId(String.valueOf(source.get("userId")));
        doc.setSessionId(String.valueOf(source.get("sessionId")));
        doc.setSummary(String.valueOf(source.get("summary")));
        doc.setRawConversation(String.valueOf(source.get("rawConversation")));
        Object createdAt = source.get("createdAt");
        if (createdAt instanceof Number) {
            doc.setCreatedAt(((Number) createdAt).longValue());
        } else {
            doc.setCreatedAt(System.currentTimeMillis());
        }
        return doc;
    }

    private void ensureIndex() {
        try {
            if (client.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT)) {
                return;
            }
            CreateIndexRequest request = new CreateIndexRequest(indexName);
            request.settings(Settings.builder().put("index.number_of_shards", 1).put("index.number_of_replicas", 0));

            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("id", mapType("keyword"));
            properties.put("userId", mapType("keyword"));
            properties.put("sessionId", mapType("keyword"));

            Map<String, Object> summary = mapType("text");
            summary.put("analyzer", "ik_max_word");
            summary.put("search_analyzer", "ik_smart");
            properties.put("summary", summary);

            Map<String, Object> rawConversation = mapType("text");
            rawConversation.put("analyzer", "ik_max_word");
            rawConversation.put("search_analyzer", "ik_smart");
            properties.put("rawConversation", rawConversation);
            properties.put("createdAt", mapType("long"));

            Map<String, Object> mapping = new HashMap<String, Object>();
            mapping.put("properties", properties);
            request.mapping(mapping);
            client.indices().create(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create memory index, please ensure IK analyzer plugin is installed", e);
        }
    }

    private Map<String, Object> mapType(String type) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("type", type);
        return map;
    }
}
