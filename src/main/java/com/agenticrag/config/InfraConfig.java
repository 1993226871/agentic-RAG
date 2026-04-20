package com.agenticrag.config;

import com.agenticrag.infra.chunk.SimpleLangchain4jChunker;
import com.agenticrag.infra.embedding.AliyunEmbeddingClient;
import com.agenticrag.infra.embedding.MockAliEmbeddingClient;
import com.agenticrag.infra.agent.HeuristicAgentPlanner;
import com.agenticrag.infra.agent.LangChain4jAgentPlanner;
import com.agenticrag.infra.es.ElasticsearchHybridStore;
import com.agenticrag.infra.es.ElasticsearchUploadFileStore;
import com.agenticrag.infra.es.InMemoryElasticsearchHybridStore;
import com.agenticrag.infra.memory.ElasticsearchConversationMemoryStore;
import com.agenticrag.infra.memory.InMemoryConversationMemoryStore;
import com.agenticrag.infra.memory.LlmConversationSummarizer;
import com.agenticrag.infra.memory.MockConversationSummarizer;
import com.agenticrag.infra.inmemory.InMemoryMessageQueue;
import com.agenticrag.infra.inmemory.InMemoryMinioChunkStorage;
import com.agenticrag.infra.inmemory.InMemoryRedisBitmapStore;
import com.agenticrag.infra.inmemory.InMemoryUploadFileStore;
import com.agenticrag.infra.minio.MinioChunkStorageImpl;
import com.agenticrag.infra.redis.RedisBitmapStoreImpl;
import com.agenticrag.infra.rerank.BgeHttpReranker;
import com.agenticrag.infra.rerank.MockBgeReranker;
import com.agenticrag.infra.rewrite.MockQueryRewriter;
import com.agenticrag.infra.rewrite.QwenHttpQueryRewriter;
import com.agenticrag.infra.rocketmq.RocketMqMessageQueue;
import com.agenticrag.infra.tika.TikaTextParser;
import com.agenticrag.ports.ConversationMemoryStore;
import com.agenticrag.ports.ConversationSummarizer;
import com.agenticrag.ports.AgentPlanner;
import com.agenticrag.ports.EmbeddingClient;
import com.agenticrag.ports.HybridSearchStore;
import com.agenticrag.ports.MessageQueue;
import com.agenticrag.ports.MinioChunkStorage;
import com.agenticrag.ports.QueryRewriter;
import com.agenticrag.ports.RedisBitmapStore;
import com.agenticrag.ports.Reranker;
import com.agenticrag.ports.TextChunker;
import com.agenticrag.ports.TikaParser;
import com.agenticrag.ports.UploadFileStore;
import io.minio.MinioClient;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestTemplate;

@Configuration
public class InfraConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    public TikaParser tikaParser() {
        return new TikaTextParser();
    }

    @Bean
    public TextChunker textChunker() {
        return new SimpleLangchain4jChunker(600, 120);
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "true", matchIfMissing = true)
    public RedisBitmapStore mockRedisBitmapStore() {
        return new InMemoryRedisBitmapStore();
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "false")
    public RedisBitmapStore redisBitmapStore(StringRedisTemplate redisTemplate) {
        return new RedisBitmapStoreImpl(redisTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "true", matchIfMissing = true)
    public MinioChunkStorage mockMinioChunkStorage() {
        return new InMemoryMinioChunkStorage();
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "false")
    public MinioChunkStorage minioChunkStorage(MinioClient minioClient, RagProperties properties) {
        return new MinioChunkStorageImpl(minioClient, properties.getUpload().getBucket());
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "false")
    public MinioClient minioClient(RagProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getUpload().getEndpoint())
                .credentials(properties.getUpload().getAccessKey(), properties.getUpload().getSecretKey())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "true", matchIfMissing = true)
    public MessageQueue mockMessageQueue() {
        return new InMemoryMessageQueue();
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "false")
    public MessageQueue messageQueue(org.apache.rocketmq.spring.core.RocketMQTemplate template, RagProperties properties) {
        return new RocketMqMessageQueue(template, properties.getMq().getTopic());
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "true", matchIfMissing = true)
    public EmbeddingClient mockEmbeddingClient(RagProperties properties) {
        return new MockAliEmbeddingClient(properties.getEs().getVectorDims());
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "false")
    public EmbeddingClient embeddingClient(RagProperties properties, RestTemplate restTemplate) {
        return new AliyunEmbeddingClient(properties, restTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "true", matchIfMissing = true)
    public HybridSearchStore mockHybridSearchStore() {
        return new InMemoryElasticsearchHybridStore();
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "true", matchIfMissing = true)
    public UploadFileStore mockUploadFileStore() {
        return new InMemoryUploadFileStore();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "false")
    public RestHighLevelClient restHighLevelClient(@Value("${spring.elasticsearch.uris}") String esUris) {
        String first = esUris.split(",")[0].trim();
        return new RestHighLevelClient(RestClient.builder(HttpHost.create(first)));
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "false")
    public HybridSearchStore hybridSearchStore(RestHighLevelClient client, RagProperties properties) {
        return new ElasticsearchHybridStore(client, properties);
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "false")
    public UploadFileStore uploadFileStore(RestHighLevelClient client, RagProperties properties) {
        return new ElasticsearchUploadFileStore(client, properties);
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "true", matchIfMissing = true)
    public Reranker mockReranker() {
        return new MockBgeReranker();
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "false")
    public Reranker reranker(RagProperties properties, RestTemplate restTemplate) {
        return new BgeHttpReranker(properties, restTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "true", matchIfMissing = true)
    public QueryRewriter mockQueryRewriter() {
        return new MockQueryRewriter();
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "false")
    public QueryRewriter queryRewriter(RagProperties properties, RestTemplate restTemplate) {
        return new QwenHttpQueryRewriter(properties, restTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "true", matchIfMissing = true)
    public ConversationMemoryStore mockConversationMemoryStore() {
        return new InMemoryConversationMemoryStore();
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "false")
    public ConversationMemoryStore conversationMemoryStore(RestHighLevelClient client, RagProperties properties) {
        return new ElasticsearchConversationMemoryStore(client, properties);
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "true", matchIfMissing = true)
    public ConversationSummarizer mockConversationSummarizer() {
        return new MockConversationSummarizer();
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "false")
    public ConversationSummarizer conversationSummarizer(RagProperties properties, RestTemplate restTemplate) {
        return new LlmConversationSummarizer(properties, restTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "true", matchIfMissing = true)
    public AgentPlanner mockAgentPlanner() {
        return new HeuristicAgentPlanner();
    }

    @Bean
    @ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "false")
    public AgentPlanner agentPlanner(RagProperties properties) {
        String baseUrl = properties.getAgent().getPlannerEndpoint();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return new HeuristicAgentPlanner();
        }
        baseUrl = normalizeOpenAiBaseUrl(baseUrl);
        String apiKey = properties.getAgent().getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = "EMPTY_API_KEY";
        }
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl(baseUrl.trim())
                .apiKey(apiKey.trim())
                .modelName(properties.getAgent().getPlannerModel())
                .temperature(0.0)
                .build();
        return new LangChain4jAgentPlanner(model, properties);
    }

    private String normalizeOpenAiBaseUrl(String configuredUrl) {
        String url = configuredUrl == null ? "" : configuredUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/chat/completions")) {
            return url.substring(0, url.length() - "/chat/completions".length());
        }
        return url;
    }
}
