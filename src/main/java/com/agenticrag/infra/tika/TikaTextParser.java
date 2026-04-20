package com.agenticrag.infra.tika;

import com.agenticrag.ports.TikaParser;
import org.apache.tika.Tika;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class TikaTextParser implements TikaParser {
    private final Tika tika = new Tika();

    @Override
    public String parse(byte[] content) {
        try {
            String parsed = tika.parseToString(new ByteArrayInputStream(content));
            if (parsed == null || parsed.trim().isEmpty()) {
                return new String(content, StandardCharsets.UTF_8);
            }
            return parsed;
        } catch (Exception e) {
            return new String(content, StandardCharsets.UTF_8);
        }
    }
}
