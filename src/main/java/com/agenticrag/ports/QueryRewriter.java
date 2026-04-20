package com.agenticrag.ports;

import java.util.List;

public interface QueryRewriter {
    List<String> rewrite(String query, int variants);
}
