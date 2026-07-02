/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.camunda.connector.agenticai.common.AgenticAiRecord;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@AgenticAiRecord
@JsonDeserialize(builder = ToolCallResult.ToolCallResultJacksonProxyBuilder.class)
public record ToolCallResult(
    @Nullable String id,
    @Nullable String name,
    @Nullable String elementId,
    @Nullable Object content,
    // engine-sourced completion timestamp (AHSP outputElement's completedAt: now()); absent for
    // results not produced by an AHSP tool element on a v11+ template, resolved by ingestion
    // normalization (see ADR 008)
    @Nullable
        @JsonSerialize(using = ToolCallResult.CompletedAtSerializer.class)
        @JsonDeserialize(using = ToolCallResult.CompletedAtDeserializer.class)
        OffsetDateTime completedAt,
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

  /**
   * Parses the FEEL {@code now()} string form produced by the AHSP outputElement, which carries an
   * offset plus a bracketed zone id (e.g. {@code 2026-07-02T11:55:00.522622+02:00[Europe/Berlin]})
   * that bare {@code OffsetDateTime.parse(text)} cannot handle. Also accepts the plain offset form
   * (no brackets), so it round-trips values written by {@link CompletedAtSerializer}.
   */
  public static final class CompletedAtDeserializer extends JsonDeserializer<OffsetDateTime> {
    @Override
    public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException {
      return OffsetDateTime.parse(p.getValueAsString(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }
  }

  public static final class CompletedAtSerializer extends JsonSerializer<OffsetDateTime> {
    @Override
    public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      gen.writeString(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value));
    }
  }
}
