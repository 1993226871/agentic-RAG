package com.agenticrag.ports;

import java.util.List;

public interface TextChunker {
    List<String> chunk(String text);
}
