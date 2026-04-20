package com.agenticrag.infra.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ReActPlanningAiService {
    @SystemMessage(
            "你是ReAct Agent规划器。你只能输出JSON，不要输出任何额外文本。" +
                    "JSON字段: action(query检索动作: retrieve_docs|retrieve_memory|finish), " +
                    "query(字符串), confidence(0到1), final_answer(字符串)."
    )
    @UserMessage(
            "当前step={{step}}, maxSteps={{maxSteps}}\n" +
                    "用户问题: {{userQuery}}\n" +
                    "当前上下文:\n{{scratchpad}}\n" +
                    "请输出JSON。"
    )
    String decide(
            @V("userQuery") String userQuery,
            @V("scratchpad") String scratchpad,
            @V("step") int step,
            @V("maxSteps") int maxSteps
    );
}
