package com.agenticrag.infra.embedding;

import com.agenticrag.ports.EmbeddingClient;

import java.util.ArrayList;
import java.util.List;

public class MockAliEmbeddingClient implements EmbeddingClient {
    private final int dimensions;

    public MockAliEmbeddingClient(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public List<Double> embed(String text) {
        String input = text == null ? "" : text;
        double[] vector = new double[dimensions];
        for (int i = 0; i < input.length(); i++) {
            int slot = i % dimensions;
            vector[slot] += (input.charAt(i) % 31) / 31.0;
        }
        List<Double> out = new ArrayList<>(dimensions);
        for (double value : vector) {
            out.add(value);
        }
        return out;
    }
}
