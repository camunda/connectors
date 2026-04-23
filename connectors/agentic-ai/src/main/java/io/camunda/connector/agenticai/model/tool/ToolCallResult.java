/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.tool;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import java.util.HashMap;
import java.util.Map;
import org.springframework.lang.Nullable;

@AgenticAiRecord
@JsonDeserialize(builder = ToolCallResult.ToolCallResultJacksonProxyBuilder.class)
public record ToolCallResult(
    @Nullable String id,
    @Nullable String name,
    @Nullable Object content,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @JsonAnySetter @JsonAnyGetter
        Map<String, Object> properties)
    implements ToolCallResultBuilder.With {

  public static final String PROPERTY_INTERRUPTED = "interrupted";
  public static final String CONTENT_NO_RESULT =
      "Tool execution succeeded, but returned no result.";
  public static final String CONTENT_CANCELLED = "Tool execution was canceled.";

  public static ToolCallResult forCancelledToolCall(String id, String name) {
    return ToolCallResult.builder()
        .id(id)
        .name(name)
        .content(CONTENT_CANCELLED)
        .properties(Map.of(PROPERTY_INTERRUPTED, true))
        .build();
  }

  public static ToolCallResultBuilder builder() {
    return ToolCallResultBuilder.builder();
  }

  /**
   * Custom proxy builder that fixes round-tripping of the {@code properties} map.
   *
   * <p>The generated {@link ToolCallResultBuilder} inherits {@code @JsonAnyGetter} on the {@code
   * properties()} getter, which flattens map entries as top-level JSON fields during serialization.
   * However, the same getter returns an immutable {@code Map.copyOf()}, so Jackson cannot add
   * unknown fields back into it during deserialization. Combined with
   * {@code @JsonIgnoreProperties(ignoreUnknown = true)} from {@link AgenticAiRecord}, unknown
   * fields are silently dropped instead of being collected into {@code properties}.
   *
   * <p>This builder provides a proper {@code @JsonAnySetter} method that collects unknown fields
   * and merges them into {@code properties} at build time.
   */
  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolCallResultJacksonProxyBuilder extends ToolCallResultBuilder {
    private final Map<String, Object> unknownProperties = new HashMap<>();

    @JsonAnySetter
    public void set(String key, Object value) {
      unknownProperties.put(key, value);
    }

    @Override
    public ToolCallResult build() {
      if (!unknownProperties.isEmpty()) {
        Map<String, Object> merged = new HashMap<>(unknownProperties);
        Map<String, Object> explicit = super.properties();
        if (explicit != null && !explicit.isEmpty()) {
          merged.putAll(explicit);
        }
        super.properties(merged);
      }
      return super.build();
    }
  }
}
