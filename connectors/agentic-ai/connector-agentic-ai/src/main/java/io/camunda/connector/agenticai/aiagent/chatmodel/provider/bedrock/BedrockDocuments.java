/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * <p>Used by every Bedrock Converse converter that needs to produce a {@link Document}: tool-result
 * {@code json} content ({@code BedrockConverseContentConverter}), tool input schemas and {@code
 * additionalModelRequestFields} ({@code BedrockConverseRequestConverter}), and parsed streamed
 * tool-use input ({@code BedrockConverseStreamAssembler}). Consolidated here (rather than
 * duplicated per call site) so all three agree on exactly one conversion policy.
 *
 * <p>Anything that isn't already a {@code Map}/{@code List}/{@code String}/{@code Number}/{@code
 * Boolean}/{@code null} is treated as an arbitrary POJO: it is normalized to its JSON tree shape
 * via {@code objectMapper.convertValue(value, Object.class)} and retried. This deliberately never
 * throws for a POJO Jackson can serialize; for the rare value neither this method nor Jackson can
 * make sense of, {@code convertValue} itself throws (typically an {@link IllegalArgumentException}
 * wrapping a {@code JsonMappingException}), which is intentionally left uncaught here. The three
 * call sites have different failure semantics (a bad tool schema vs. a malformed streamed response
 * vs. an unsupported request parameter), so each catches and wraps that generic failure into its
 * own {@code ConnectorException} with its own appropriate error code, rather than this shared
 * helper picking one error code for all of them.
 */
final class BedrockDocuments {

  private BedrockDocuments() {}

  static Document toDocument(@Nullable Object value, ObjectMapper objectMapper) {
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
    if (value instanceof Map<?, ?> map) {
      final Map<String, Document> result = new LinkedHashMap<>();
      map.forEach((k, v) -> result.put(String.valueOf(k), toDocument(v, objectMapper)));
      return Document.fromMap(result);
    }
    if (value instanceof List<?> list) {
      final List<Document> result = new ArrayList<>(list.size());
      for (final Object element : list) {
        result.add(toDocument(element, objectMapper));
      }
      return Document.fromList(result);
    }
    // Arbitrary POJO: normalize to its JSON tree shape (Map/List/scalar) via Jackson and retry.
    return toDocument(objectMapper.convertValue(value, Object.class), objectMapper);
  }
}
