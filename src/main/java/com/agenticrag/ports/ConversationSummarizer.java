package com.agenticrag.ports;

import com.agenticrag.model.ConversationTurn;

import java.util.List;

public interface ConversationSummarizer {
    String summarize(String userId, String sessionId, List<ConversationTurn> turns);
}
