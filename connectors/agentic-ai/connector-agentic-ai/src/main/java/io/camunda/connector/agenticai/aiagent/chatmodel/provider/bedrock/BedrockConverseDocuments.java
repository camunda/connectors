/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.core.document.Document;

/**
 * Shared conversion from an arbitrary already-deserialized JSON-tree-shaped Java value (as produced
 * by Jackson deserialization or FEEL evaluation: maps, lists, strings, numbers, booleans, null - or
 * an arbitrary POJO) into the AWS SDK's generic {@code Document} value tree.
 *
 * <p>Note the name collision this class sits on top of: the {@link Document} it produces is the AWS
 * SDK's untyped JSON value tree (the SDK's equivalent of a {@code JsonNode}, used for schema-free
 * request/response members such as {@code toolResult.content[].json}), and has nothing to do with
 * either a Camunda {@link io.camunda.connector.api.document.Document} (a binary attachment plus its
 * store reference) or a Converse {@code DocumentBlock} (Bedrock's native content block for feeding
 * a file's bytes to the model).
 *
 * <p>Used by every Bedrock Converse converter that needs to produce a {@link Document}: tool-result
 * {@code json} content ({@code BedrockConverseContentConverter}), tool input schemas and {@code
 * additionalModelRequestFields} ({@code BedrockConverseRequestConverter}), and parsed streamed
 * tool-use input ({@code BedrockConverseStreamAssembler}). Consolidated here (rather than
 * duplicated per call site) so all three agree on exactly one conversion policy.
 *
 * <p>A Camunda {@link io.camunda.connector.api.document.Document} nested anywhere in the tree (e.g.
 * a tool result whose JSON embeds one or more documents, per {@code
 * io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent#contentFromObject}, which
 * lifts any non-string tool result into a single {@code ObjectContent} without extracting embedded
 * documents first) is serialized to the JSON reference shape the runtime's registered Document
 * Jackson module writes ({@code documentId}/{@code storeId}, or the external URL, plus metadata)
 * and that tree is converted structurally. It is deliberately never handed back to Jackson for
 * deserialization: {@code objectMapper.convertValue(document, Object.class)} round-trips through
 * that same module, which deserializes an {@code Object.class} target back into another {@code
 * Document} instance instead of a plain map - so treating it as an arbitrary POJO would recurse
 * forever.
 *
 * <p>A document nested in a tool result is embedded in this tree only as that same reference, never
 * inlined as native bytes: its actual bytes are already delivered to the model elsewhere, so
 * inlining them here as well would send them twice and trip Bedrock's "duplicate document names"
 * validation, since {@link DocumentHandle#idFor} derives the same {@code DocumentBlock.name} in
 * both places.
 *
 * <p>Anything else that isn't already a {@code Map}/{@code List}/{@code String}/{@code Number}/
 * {@code Boolean}/{@code null} is treated as an arbitrary POJO: it is normalized to its JSON tree
 * shape via {@code objectMapper.convertValue(value, Object.class)} and retried. This deliberately
 * never throws for a POJO Jackson can serialize; for the rare value neither this method nor Jackson
 * can make sense of, {@code convertValue} itself throws (typically an {@link
 * IllegalArgumentException} wrapping a {@code JsonMappingException}), which is intentionally left
 * uncaught here. The three call sites have different failure semantics (a bad tool schema vs. a
 * malformed streamed response vs. an unsupported request parameter), so each catches and wraps that
 * generic failure into its own {@code ConnectorException} with its own appropriate error code,
 * rather than this shared helper picking one error code for all of them.
 */
final class BedrockConverseDocuments {

  private BedrockConverseDocuments() {}

  static Document toAwsDocument(@Nullable Object value, ObjectMapper objectMapper) {
    if (value == null) {
      return Document.fromNull();
    }
    if (value instanceof Boolean bool) {
      return Document.fromBoolean(bool);
    }
    if (value instanceof String str) {
      return Document.fromString(str);
    }
    if (value instanceof Number number) {
      return Document.fromNumber(number.toString());
    }
    if (value instanceof io.camunda.connector.api.document.Document document) {
      return fromJsonNode(objectMapper.valueToTree(document));
    }
    if (value instanceof JsonNode node) {
      return fromJsonNode(node);
    }
    if (value instanceof Map<?, ?> map) {
      final Map<String, Document> result = new LinkedHashMap<>();
      map.forEach((k, v) -> result.put(String.valueOf(k), toAwsDocument(v, objectMapper)));
      return Document.fromMap(result);
    }
    if (value instanceof List<?> list) {
      final List<Document> result = new ArrayList<>(list.size());
      for (final Object element : list) {
        result.add(toAwsDocument(element, objectMapper));
      }
      return Document.fromList(result);
    }
    // Arbitrary POJO: normalize to its JSON tree shape (Map/List/scalar) via Jackson and retry.
    return toAwsDocument(objectMapper.convertValue(value, Object.class), objectMapper);
  }

  /**
   * Converts an already-serialized Jackson tree, structurally and without handing anything back to
   * Jackson for deserialization.
   */
  private static Document fromJsonNode(JsonNode node) {
    return switch (node.getNodeType()) {
      case BOOLEAN -> Document.fromBoolean(node.booleanValue());
      case NUMBER -> Document.fromNumber(node.numberValue().toString());
      case STRING, BINARY -> Document.fromString(node.asText());
      case OBJECT, POJO -> {
        final Map<String, Document> result = new LinkedHashMap<>();
        node.properties()
            .forEach(entry -> result.put(entry.getKey(), fromJsonNode(entry.getValue())));
        yield Document.fromMap(result);
      }
      case ARRAY -> {
        final List<Document> result = new ArrayList<>(node.size());
        node.forEach(element -> result.add(fromJsonNode(element)));
        yield Document.fromList(result);
      }
      case NULL, MISSING -> Document.fromNull();
    };
  }
}
