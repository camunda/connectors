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
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
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
import io.camunda.connector.document.jackson.deserializer.DeserializationUtil;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The persisted tool-call-result element type (as opposed to {@link ToolCallResult}, the transient
 * tool-return DTO which keeps its untyped {@code Object content}). Unlike {@link ToolCallResult},
 * this type carries a structured {@link Content} list so later stages (capability-aware routing,
 * native providers) can inspect the shape of a tool result without stringifying it.
 *
 * <p><b>Backward compatibility (Camunda 8.9):</b> before this type existed, a persisted tool call
 * result was a flat JSON object matching {@link ToolCallResult} — {@code content} was a raw
 * scalar/object/array and any additional (framework-internal) properties were flattened as
 * top-level fields. {@link ToolCallResultContentJacksonProxyBuilder} accepts both shapes losslessly
 * (see {@link ContentJsonDeserializer} for the content-shape heuristic).
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

  public ToolCallResultContent {
    content = content == null ? List.of() : List.copyOf(content);
    properties =
        properties == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(properties));
  }

  /**
   * Returns the {@link ToolCallResultContentJacksonProxyBuilder} rather than the plain generated
   * builder: the generated builder's own {@code build()} unconditionally runs {@code
   * Map.copyOf(properties)} (rejecting null values) before the compact constructor above ever
   * executes, which would defeat the null-tolerant handling there for any caller going through
   * {@code builder()} (not just Jackson deserialization).
   */
  public static ToolCallResultContentBuilder builder() {
    return new ToolCallResultContentJacksonProxyBuilder();
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
   * The content-lift mapping shared by the forward path ({@link #from(ToolCallResult)}, operating
   * on already-deserialized Java objects) and the backward-compat proxy builder ({@link
   * ContentJsonDeserializer}, operating on raw JSON nodes read from 8.9 data). Each branch produces
   * a singleton list (or an empty one for null/blank) — a multi-element list only ever arises later
   * once native providers emit structured content directly.
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
   * interrupted: true}) into {@code properties} at build time, since the generated builder's
   * {@code @JsonAnyGetter} getter returns an immutable copy that Jackson cannot mutate during
   * deserialization.
   *
   * <p>It also overrides the generated {@code content(...)} setter to route through {@link
   * ContentJsonDeserializer}. Annotating the record component itself is not enough here: unlike
   * {@code completedAt} (a simple {@code OffsetDateTime}), Jackson does not honor a
   * {@code @JsonDeserialize} placed on a fluent builder setter's *parameter* for a collection-typed
   * property — it silently falls back to the default (here: polymorphic) collection deserializer.
   * The annotation must sit on the setter *method* itself (confirmed against plain Jackson without
   * any RecordBuilder/connectors involvement).
   *
   * <p>Finally, {@link #build()} constructs the record directly instead of delegating to the
   * generated {@code super.build()}: the generated implementation unconditionally applies {@code
   * Map.copyOf(properties)} (rejecting null values) to the {@code properties} field before the
   * compact constructor above ever runs, and the generated {@code properties()} getter it would
   * otherwise be tempting to read the current value from applies the same rejecting copy. Tracking
   * the explicitly-set properties map in {@link #explicitProperties} instead of reading it back via
   * that getter keeps the null-tolerant compact constructor above effective for 8.9 data where a
   * flattened property was persisted with a JSON-null value.
   */
  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolCallResultContentJacksonProxyBuilder
      extends ToolCallResultContentBuilder {
    private final Map<String, Object> unknownProperties = new HashMap<>();
    private @Nullable Map<String, Object> explicitProperties;

    @JsonAnySetter
    public void set(String key, Object value) {
      unknownProperties.put(key, value);
    }

    @Override
    public ToolCallResultContentBuilder properties(Map<String, Object> properties) {
      this.explicitProperties = properties;
      return super.properties(properties);
    }

    @Override
    @JsonDeserialize(using = ContentJsonDeserializer.class)
    public ToolCallResultContentBuilder content(List<Content> content) {
      return super.content(content);
    }

    @Override
    public ToolCallResultContent build() {
      Map<String, Object> properties = explicitProperties != null ? explicitProperties : Map.of();
      if (!unknownProperties.isEmpty()) {
        Map<String, Object> merged = new HashMap<>(unknownProperties);
        merged.putAll(properties);
        properties = merged;
      }
      return new ToolCallResultContent(
          id(), name(), content(), elementId(), completedAt(), properties);
    }
  }

  /**
   * Deserializes the {@code content} field, accepting both the new structured format and 8.9's flat
   * legacy format.
   *
   * <p>Distinguishing the two is purely a shape test on the {@code content} JSON node: the new
   * format is always a JSON array whose elements are objects carrying a {@code "type"}
   * discriminator (one of {@link Content}'s {@code @JsonSubTypes} names), because that's exactly
   * how a {@code List<Content>} serializes. Legacy {@code content} is a bare scalar, a plain
   * object, or an array of untyped values — it can never take that exact shape. An empty JSON array
   * is treated as new-format (an empty content list); in practice legacy data never persists a bare
   * {@code []} for {@code content} (a tool returning an empty list is exceedingly rare, and even
   * then this only affects that one edge case, not the common shapes exercised by real 8.9 data).
   */
  static final class ContentJsonDeserializer extends JsonDeserializer<List<Content>> {

    private static final Set<String> CONTENT_TYPE_DISCRIMINATORS =
        Set.of("text", "document", "object", "reasoning");

    @Override
    public List<Content> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      JsonNode node = p.readValueAsTree();
      if (node == null || node.isMissingNode() || node.isNull()) {
        return List.of();
      }

      if (isNewFormat(node)) {
        JavaType listOfContentType =
            ctxt.getTypeFactory().constructCollectionType(List.class, Content.class);
        return ctxt.readTreeAsValue(node, listOfContentType);
      }

      return legacyContent(node, ctxt);
    }

    private boolean isNewFormat(JsonNode node) {
      if (!node.isArray()) {
        return false;
      }
      for (JsonNode element : node) {
        if (!element.isObject()
            || !CONTENT_TYPE_DISCRIMINATORS.contains(element.path("type").asText(""))) {
          return false;
        }
      }
      return true;
    }

    private List<Content> legacyContent(JsonNode node, DeserializationContext ctxt)
        throws IOException {
      if (node.isTextual()) {
        String text = node.textValue();
        return (text == null || text.isBlank())
            ? List.of()
            : List.of(TextContent.textContent(text));
      }

      if (DeserializationUtil.isDocumentReference(node)) {
        Document document = ctxt.readTreeAsValue(node, Document.class);
        return List.of(DocumentContent.documentContent(document));
      }

      // any other object, or an array of non-Content-typed values, or a number/boolean —
      // deserialize to its natural Java type (routes through the connectors document module's
      // Object deserializer, resolving any nested document references/intrinsic functions)
      Object value = ctxt.readTreeAsValue(node, Object.class);
      return List.of(ObjectContent.objectContent(value));
    }
  }
}
