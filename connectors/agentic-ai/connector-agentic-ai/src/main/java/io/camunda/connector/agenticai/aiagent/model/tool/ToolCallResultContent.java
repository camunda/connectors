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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.common.AgenticAiRecord;
import io.camunda.connector.agenticai.common.util.FeelOffsetDateTimeDeserializer;
import io.camunda.connector.agenticai.common.util.FeelOffsetDateTimeSerializer;
import io.camunda.connector.api.document.Document;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The persisted tool-call-result element type (as opposed to {@link ToolCallResult}, the transient
 * tool-return DTO which keeps its untyped {@code Object content}). Unlike {@link ToolCallResult},
 * this type carries a structured {@link Content} list so later stages (capability-aware routing,
 * provider-specific chat model implementations) can inspect the shape of a tool result without
 * stringifying it.
 *
 * <p><b>Backward compatibility (Camunda 8.9):</b> before this type existed, a persisted tool call
 * result was a flat JSON object matching {@link ToolCallResult} — {@code content} was a raw
 * scalar/object/array and any additional (framework-internal) properties were flattened as
 * top-level fields. This type's {@code content} field only deserializes the current structured
 * shape; legacy state is migrated on read by an explicit persisted schema version at each
 * conversation root plus a shared upcaster (see {@code ConversationSchemaMigration}), not by
 * inspecting the shape of {@code content} — that heuristic was ambiguous with gateway tool results
 * persisted as a list of provider content blocks sharing the same type discriminators. The write
 * path always re-persists this type in its structured shape — a conversation touched again after
 * upgrading is rewritten into the new format on its next write, even if it was originally read from
 * 8.9 data.
 */
@AgenticAiRecord
@JsonDeserialize(builder = ToolCallResultContent.ToolCallResultContentJacksonProxyBuilder.class)
public record ToolCallResultContent(
    @Nullable String id,
    @Nullable String name,
    List<Content> content,
    @Nullable String elementId,
    @Nullable
        @JsonSerialize(using = FeelOffsetDateTimeSerializer.class)
        @JsonDeserialize(using = FeelOffsetDateTimeDeserializer.class)
        OffsetDateTime completedAt,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @JsonAnySetter @JsonAnyGetter
        Map<String, Object> properties)
    implements ToolCallResultContentBuilder.With {

  public static ToolCallResultContentBuilder builder() {
    return ToolCallResultContentBuilder.builder();
  }

  /**
   * Forward normalization: lifts a live {@link ToolCallResult}'s untyped {@code content} into the
   * structured {@link Content} list, preserving every other field verbatim.
   */
  public static ToolCallResultContent from(ToolCallResult result) {
    return builder()
        .id(result.id())
        .name(result.name())
        .elementId(result.elementId())
        .completedAt(result.completedAt())
        .properties(result.properties())
        .content(contentFromObject(result.content()))
        .build();
  }

  /**
   * The content-lift mapping for the forward path ({@link #from(ToolCallResult)}, operating on
   * already-deserialized Java objects). The mirror-image lift for raw JSON nodes read from legacy
   * (Camunda 8.9) data lives in {@code ConversationSchemaMigration#liftLegacyContent}. Each branch
   * produces a singleton list (or an empty one for null/blank) — a multi-element list only ever
   * arises later once provider-specific chat model implementations emit structured content
   * directly.
   */
  static List<Content> contentFromObject(@Nullable Object content) {
    return switch (content) {
      case null -> List.of();
      case String s -> s.isBlank() ? List.of() : List.of(TextContent.textContent(s));
      case Document document -> List.of(DocumentContent.documentContent(document));
      default -> List.of(ObjectContent.objectContent(content));
    };
  }

  /**
   * Custom proxy builder mirroring {@link ToolCallResult.ToolCallResultJacksonProxyBuilder}: it
   * collects unknown top-level fields (the flattened {@code properties} of 8.9 data, e.g. {@code
   * interrupted: true}) via {@code @JsonAnySetter}, since the generated builder's immutable-copy
   * getter can't be mutated by Jackson during deserialization.
   */
  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolCallResultContentJacksonProxyBuilder
      extends ToolCallResultContentBuilder {
    private final Map<String, Object> unknownProperties = new HashMap<>();

    @JsonAnySetter
    public void set(String key, Object value) {
      unknownProperties.put(key, value);
    }

    @Override
    public ToolCallResultContent build() {
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
