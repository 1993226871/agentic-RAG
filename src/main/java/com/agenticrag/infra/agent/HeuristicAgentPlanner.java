package com.agenticrag.infra.agent;

import com.agenticrag.ports.AgentPlanner;
import com.agenticrag.service.agent.AgentDecision;

public class HeuristicAgentPlanner implements AgentPlanner {
    @Override
    public AgentDecision decide(String userQuery, String scratchpad, int step, int maxSteps) {
        if (step == 1) {
            return new AgentDecision(AgentDecision.ACTION_RETRIEVE_MEMORY, userQuery, "", 0.35);
        }
        if (step == 2) {
            return new AgentDecision(AgentDecision.ACTION_RETRIEVE_DOCS, userQuery, "", 0.55);
        }
        if (step >= maxSteps) {
            return new AgentDecision(AgentDecision.ACTION_FINISH, userQuery, "", 0.9);
        }
        if (scratchpad != null && scratchpad.contains("证据数量: 0")) {
            return new AgentDecision(AgentDecision.ACTION_RETRIEVE_DOCS, userQuery, "", 0.6);
        }
        return new AgentDecision(AgentDecision.ACTION_FINISH, userQuery, "", 0.85);
    }
}
