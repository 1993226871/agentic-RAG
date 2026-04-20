package com.agenticrag.infra.rewrite;

import com.agenticrag.ports.QueryRewriter;

import java.util.ArrayList;
import java.util.List;

public class MockQueryRewriter implements QueryRewriter {
    @Override
    public List<String> rewrite(String query, int variants) {
        List<String> rewrites = new ArrayList<String>();
        if (query == null || query.trim().isEmpty() || variants <= 0) {
            return rewrites;
        }
        String q = query.trim();
        rewrites.add("请从实现步骤角度回答: " + q);
        if (variants > 1) {
            rewrites.add("请提取关键词并检索相关知识: " + q);
        }
        if (variants > 2) {
            rewrites.add("请结合同义词和扩展表达检索: " + q);
        }
        return rewrites;
    }
}
