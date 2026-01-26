package io.camunda.connector.agenticai.model.message.content;

import java.util.Map;

public record BinaryContent(byte[] blob, String mimeType, Map<String,Object> metadata) implements Content {
    @Override
    public Map<String, Object> metadata() {
        return Map.of();
    }
}
