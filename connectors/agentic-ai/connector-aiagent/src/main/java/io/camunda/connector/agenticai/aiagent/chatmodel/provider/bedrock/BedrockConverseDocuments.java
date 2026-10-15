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
 * Shared conversion from an arbitrary already-deserialized JSON-tree-shaped Java value (map, list,
 * string, number, boolean, null, or POJO) into the AWS SDK's generic {@code Document} value tree.
 * Used by every Bedrock Converse converter that needs one (tool-result {@code json} content, tool
 * input schemas, {@code additionalModelRequestFields}, parsed streamed tool-use input),
 * consolidated here so all three agree on one conversion policy.
 *
 * <p>Name collision: the {@link Document} produced here is the AWS SDK's untyped JSON value tree
 * (like a {@code JsonNode}), unrelated to either a Camunda {@link
 * io.camunda.connector.api.document.Document} (a binary attachment) or a Converse {@code
 * DocumentBlock} (Bedrock's native file-content block).
 *
 * <p>A Camunda {@link io.camunda.connector.api.document.Document} nested in the input is converted
 * to its JSON reference shape structurally, never handed back to Jackson for deserialization (which
 * would recurse forever through the runtime's Document module), and never inlined as native bytes:
 * those are already delivered to the model elsewhere, and {@link DocumentHandle#idFor} derives the
 * same {@code DocumentBlock.name} in both places, so a second inlined copy would trip Bedrock's
 * duplicate-document-name validation.
 *
 * <p>Any other POJO is normalized to its JSON tree shape via Jackson and retried.
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
