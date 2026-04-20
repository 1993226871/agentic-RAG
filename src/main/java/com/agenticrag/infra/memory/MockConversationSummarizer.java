package com.agenticrag.infra.memory;

import com.agenticrag.model.ConversationTurn;
import com.agenticrag.ports.ConversationSummarizer;

import java.util.List;

public class MockConversationSummarizer implements ConversationSummarizer {
    @Override
    public String summarize(String userId, String sessionId, List<ConversationTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("用户 ").append(userId).append(" 会话 ").append(sessionId).append(" 摘要：");
        int keep = Math.min(turns.size(), 6);
        for (int i = Math.max(0, turns.size() - keep); i < turns.size(); i++) {
            ConversationTurn turn = turns.get(i);
            String shortText = turn.getContent() == null ? "" : turn.getContent().trim();
            if (shortText.length() > 80) {
                shortText = shortText.substring(0, 80) + "...";
            }
            sb.append("[").append(turn.getRole()).append("] ").append(shortText).append(" ");
        }
        return sb.toString().trim();
    }
}
