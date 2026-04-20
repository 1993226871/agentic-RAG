package com.agenticrag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {
    private boolean mockEnabled = true;
    private Upload upload = new Upload();
    private Mq mq = new Mq();
    private Es es = new Es();
    private Embedding embedding = new Embedding();
    private Rerank rerank = new Rerank();
    private Rewrite rewrite = new Rewrite();
    private Memory memory = new Memory();
    private Agent agent = new Agent();

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }

    public Upload getUpload() {
        return upload;
    }

    public void setUpload(Upload upload) {
        this.upload = upload;
    }

    public Mq getMq() {
        return mq;
    }

    public void setMq(Mq mq) {
        this.mq = mq;
    }

    public Es getEs() {
        return es;
    }

    public void setEs(Es es) {
        this.es = es;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }

    public Rerank getRerank() {
        return rerank;
    }

    public void setRerank(Rerank rerank) {
        this.rerank = rerank;
    }

    public Rewrite getRewrite() {
        return rewrite;
    }

    public void setRewrite(Rewrite rewrite) {
        this.rewrite = rewrite;
    }

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    public static class Upload {
        private String bucket = "rag-bucket";
        private String endpoint = "http://localhost:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }

    public static class Mq {
        private String topic = "rag-upload-complete";
        private String consumerGroup = "rag-consumer-group";

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getConsumerGroup() {
            return consumerGroup;
        }

        public void setConsumerGroup(String consumerGroup) {
            this.consumerGroup = consumerGroup;
        }
    }

    public static class Es {
        private String index = "rag_chunks";
        private int vectorDims = 8;

        public String getIndex() {
            return index;
        }

        public void setIndex(String index) {
            this.index = index;
        }

        public int getVectorDims() {
            return vectorDims;
        }

        public void setVectorDims(int vectorDims) {
            this.vectorDims = vectorDims;
        }
    }

    public static class Embedding {
        private String provider = "alibaba";
        private String endpoint;
        private String apiKey;
        private String model = "qwen3-vl-embedding";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Rerank {
        private String provider = "bge";
        private String endpoint;
        private String apiKey;
        private String model = "qwen3-vl-rerank";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Rewrite {
        private String provider = "qwen";
        private String endpoint = "";
        private String apiKey = "";
        private String model = "qwen-turbo";
        private int variants = 3;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getVariants() {
            return variants;
        }

        public void setVariants(int variants) {
            this.variants = variants;
        }
    }

    public static class Memory {
        private String index = "rag_memory";
        private int historyTopK = 3;
        private int summarizeEveryTurns = 20;
        private String summaryEndpoint = "";
        private String summaryModel = "qwen-max";
        private String apiKey = "";

        public String getIndex() {
            return index;
        }

        public void setIndex(String index) {
            this.index = index;
        }

        public int getHistoryTopK() {
            return historyTopK;
        }

        public void setHistoryTopK(int historyTopK) {
            this.historyTopK = historyTopK;
        }

        public int getSummarizeEveryTurns() {
            return summarizeEveryTurns;
        }

        public void setSummarizeEveryTurns(int summarizeEveryTurns) {
            this.summarizeEveryTurns = summarizeEveryTurns;
        }

        public String getSummaryEndpoint() {
            return summaryEndpoint;
        }

        public void setSummaryEndpoint(String summaryEndpoint) {
            this.summaryEndpoint = summaryEndpoint;
        }

        public String getSummaryModel() {
            return summaryModel;
        }

        public void setSummaryModel(String summaryModel) {
            this.summaryModel = summaryModel;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    public static class Agent {
        private boolean enabled = true;
        private int maxSteps = 5;
        private int docTopK = 3;
        private int memoryTopK = 3;
        private String plannerEndpoint = "";
        private String plannerModel = "qwen-plus";
        private String apiKey = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxSteps() {
            return maxSteps;
        }

        public void setMaxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
        }

        public int getDocTopK() {
            return docTopK;
        }

        public void setDocTopK(int docTopK) {
            this.docTopK = docTopK;
        }

        public int getMemoryTopK() {
            return memoryTopK;
        }

        public void setMemoryTopK(int memoryTopK) {
            this.memoryTopK = memoryTopK;
        }

        public String getPlannerEndpoint() {
            return plannerEndpoint;
        }

        public void setPlannerEndpoint(String plannerEndpoint) {
            this.plannerEndpoint = plannerEndpoint;
        }

        public String getPlannerModel() {
            return plannerModel;
        }

        public void setPlannerModel(String plannerModel) {
            this.plannerModel = plannerModel;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
