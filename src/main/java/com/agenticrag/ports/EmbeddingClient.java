package com.agenticrag.ports;

import java.util.List;

public interface EmbeddingClient {
    List<Double> embed(String text);
}
