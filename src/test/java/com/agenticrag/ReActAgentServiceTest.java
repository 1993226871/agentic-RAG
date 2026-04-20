package com.agenticrag;

import com.agenticrag.config.RagProperties;
import com.agenticrag.infra.embedding.MockAliEmbeddingClient;
import com.agenticrag.infra.es.InMemoryElasticsearchHybridStore;
import com.agenticrag.infra.memory.InMemoryConversationMemoryStore;
import com.agenticrag.infra.memory.MockConversationSummarizer;
import com.agenticrag.infra.rerank.MockBgeReranker;
import com.agenticrag.model.ChunkDocument;
import com.agenticrag.model.MemorySummaryDocument;
import com.agenticrag.model.QaResult;
import com.agenticrag.ports.AgentPlanner;
import com.agenticrag.ports.QueryRewriter;
import com.agenticrag.service.OnlineQaService;
import com.agenticrag.service.ReActAgentService;
import com.agenticrag.service.agent.AgentDecision;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

class ReActAgentServiceTest {
    @Test
    void shouldIterateByPlannerAndStopWithinMaxSteps() {
        MockAliEmbeddingClient embedding = new MockAliEmbeddingClient(8);
        InMemoryElasticsearchHybridStore store = new InMemoryElasticsearchHybridStore();
        InMemoryConversationMemoryStore memoryStore = new InMemoryConversationMemoryStore();
        memoryStore.save(new MemorySummaryDocument("m-1", "root", "s1", "用户关注RAG检索流程", "user: RAG", System.currentTimeMillis()));
        store.save(new ChunkDocument("d-1", "root:md5x", "RAG 可先做检索再生成回答。", embedding.embed("RAG 可先做检索再生成回答。")));

        QueryRewriter passThrough = (q, v) -> Arrays.asList(q);
        OnlineQaService onlineQaService = new OnlineQaService(
                embedding,
                store,
                new MockBgeReranker(),
                passThrough,
                memoryStore,
                new MockConversationSummarizer(),
                60,
                1,
                3,
                20
        );

        AgentPlanner planner = new AgentPlanner() {
            @Override
            public AgentDecision decide(String userQuery, String scratchpad, int step, int maxSteps) {
                if (step == 1) {
                    return new AgentDecision(AgentDecision.ACTION_RETRIEVE_MEMORY, userQuery, "", 0.4);
                }
                if (step == 2) {
                    return new AgentDecision(AgentDecision.ACTION_RETRIEVE_DOCS, userQuery, "", 0.6);
                }
                return new AgentDecision(AgentDecision.ACTION_FINISH, userQuery, "最终答案", 0.9);
            }
        };

        RagProperties props = new RagProperties();
        props.getAgent().setEnabled(true);
        props.getAgent().setMaxSteps(5);
        props.getAgent().setDocTopK(2);
        props.getAgent().setMemoryTopK(2);

        ReActAgentService agentService = new ReActAgentService(onlineQaService, planner, props);
        QaResult result = agentService.askScoped("什么是RAG", 1, "root:md5x", "root", "s1");

        Assertions.assertTrue(result.getAnswer().contains("未配置问答生成模型"));
        Assertions.assertFalse(result.getContexts().isEmpty());
        Assertions.assertTrue(result.getStepsUsed() <= 5);
        Assertions.assertEquals("enough_evidence", result.getStopReason());
        Assertions.assertEquals(3, result.getAgentTrace().size());
    }
}
