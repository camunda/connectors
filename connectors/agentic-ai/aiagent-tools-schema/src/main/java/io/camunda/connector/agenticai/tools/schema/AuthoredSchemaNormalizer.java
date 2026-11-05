/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.tools.schema;

import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_ANYOF;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_FORMAT;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_ONEOF;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.TYPE_STRING;

import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter.Step;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the shorthand type {@value #TYPE_DOCUMENT} that an authored {@code fromAi} schema uses for
 * a document into the string schema with the format {@link DocumentParameter#FORMAT_DOCUMENT_ID},
 * the form of a {@link DocumentParameter document parameter}.
 *
 * <p>A node is normalized where a document parameter can be found: at the top level, in the object
 * {@code properties} and the array {@code items} (see {@link Step#mapChildren}), and among the
 * direct {@code anyOf} and {@code oneOf} alternatives of such a node. The shorthand anywhere else,
 * such as inside an object alternative, is left as authored: it is no document parameter, and the
 * format that marks one must not appear where nothing resolves it.
 */
final class AuthoredSchemaNormalizer {

  /**
   * Type that an authored schema uses as a shorthand for a document parameter. A node whose {@code
   * type} is an array that lists it next to other types and that has no {@code anyOf} or {@code
   * oneOf} becomes a node with an {@code anyOf} of one alternative per type, in the place of the
   * {@code type} entry. The other entries of a node keep their order and values.
   */
  static final String TYPE_DOCUMENT = "document";

  Map<String, Object> normalize(Map<String, Object> schema) {
    final var node = alternativesOf(normalizeType(schema));
    return Step.mapChildren(node, (step, child) -> normalize(child));
  }

  /** The node with its direct {@code anyOf} and {@code oneOf} alternatives normalized. */
  private Map<String, Object> alternativesOf(Map<String, Object> node) {
    final var result = new LinkedHashMap<>(node);
    for (final var keyword : List.of(PROPERTY_ANYOF, PROPERTY_ONEOF)) {
      if (result.get(keyword) instanceof List<?> alternatives) {
        final var mapped = new ArrayList<Object>();
        for (final var alternative : alternatives) {
          mapped.add(alternative instanceof Map<?, ?> map ? normalizeType(cast(map)) : alternative);
        }
        result.put(keyword, Collections.unmodifiableList(mapped));
      }
    }
    return result;
  }

  private Map<String, Object> normalizeType(Map<String, Object> node) {
    if (TYPE_DOCUMENT.equals(node.get(PROPERTY_TYPE))) {
      final var result = new LinkedHashMap<>(node);
      result.put(PROPERTY_TYPE, TYPE_STRING);
      result.put(PROPERTY_FORMAT, DocumentParameter.FORMAT_DOCUMENT_ID);
      return result;
    }
    if (node.get(PROPERTY_TYPE) instanceof List<?> types
        && types.contains(TYPE_DOCUMENT)
        && !node.containsKey(PROPERTY_ANYOF)
        && !node.containsKey(PROPERTY_ONEOF)) {
      return typeArrayToAlternatives(node, types);
    }
    return new LinkedHashMap<>(node);
  }

  private Map<String, Object> typeArrayToAlternatives(Map<String, Object> node, List<?> types) {
    final var alternatives = new ArrayList<Map<String, Object>>();
    for (final var type : types) {
      final var alternative = new LinkedHashMap<String, Object>();
      if (TYPE_DOCUMENT.equals(type)) {
        alternative.put(PROPERTY_TYPE, TYPE_STRING);
        alternative.put(PROPERTY_FORMAT, DocumentParameter.FORMAT_DOCUMENT_ID);
      } else {
        alternative.put(PROPERTY_TYPE, type);
      }
      alternatives.add(alternative);
    }

    final var result = new LinkedHashMap<String, Object>();
    node.forEach((key, value) -> {
      if (!PROPERTY_TYPE.equals(key)) {
        result.put(key, value);
      } else if (alternatives.size() == 1) {
        result.putAll(alternatives.getFirst());
      } else {
        result.put(PROPERTY_ANYOF, alternatives);
      }
    });
    return result;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> cast(Map<?, ?> map) {
    return (Map<String, Object>) map;
  }
}
