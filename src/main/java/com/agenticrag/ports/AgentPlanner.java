package com.agenticrag.ports;

import com.agenticrag.service.agent.AgentDecision;

public interface AgentPlanner {
    AgentDecision decide(String userQuery, String scratchpad, int step, int maxSteps);
}
