/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.tools.schema;

import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_ANYOF;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_ONEOF;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_TYPE;

import io.camunda.connector.agenticai.aiagent.api.tool.DocumentSchemas;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter.Step;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the shorthand type {@value #TYPE_DOCUMENT} that an authored {@code fromAi} schema uses for
 * a document into the object schema of a document handle (see {@link DocumentSchemas#document}),
 * the form of a {@link DocumentParameter document parameter}.
 *
 * <p>A node is normalized where the agent resolves a document parameter: at the top level, in the
 * object {@code properties} and the array {@code items} (see {@link Step#mapChildren}), and in the
 * {@code anyOf} and {@code oneOf} alternatives of such a node at any depth, with the same rules
 * inside an alternative. The shorthand anywhere else, such as under {@code allOf}, {@code not},
 * {@code if}/{@code then}/{@code else}, {@code additionalProperties}, {@code patternProperties},
 * {@code prefixItems}, {@code $defs} or behind {@code $ref}, is left as authored: no document
 * parameter is found there, and the handle schema must not appear where nothing resolves it.
 *
 * <p>A {@code type} array that lists {@value #TYPE_DOCUMENT} is rejected where a node is
 * normalized: a union that can hold a document is an explicit {@code anyOf} or {@code oneOf} with
 * {@code {"type": "document"}} as one alternative.
 */
final class AuthoredSchemaNormalizer {

  /** Type that an authored schema uses as a shorthand for a document parameter. */
  static final String TYPE_DOCUMENT = "document";

  /**
   * The schema with the shorthand turned into the handle schema.
   *
   * @throws IllegalArgumentException if a normalized node has a {@code type} array that lists
   *     {@value #TYPE_DOCUMENT}
   */
  Map<String, Object> normalize(Map<String, Object> schema) {
    final var node = alternativesOf(normalizeType(schema));
    return Step.mapChildren(node, (step, child) -> normalize(child));
  }

  /** The node with its {@code anyOf} and {@code oneOf} alternatives normalized. */
  private Map<String, Object> alternativesOf(Map<String, Object> node) {
    final var result = new LinkedHashMap<>(node);
    for (final var keyword : List.of(PROPERTY_ANYOF, PROPERTY_ONEOF)) {
      if (result.get(keyword) instanceof List<?> alternatives) {
        final var mapped = new ArrayList<Object>();
        for (final var alternative : alternatives) {
          mapped.add(alternative instanceof Map<?, ?> map ? normalize(cast(map)) : alternative);
        }
        result.put(keyword, Collections.unmodifiableList(mapped));
      }
    }
    return result;
  }

  private Map<String, Object> normalizeType(Map<String, Object> node) {
    if (TYPE_DOCUMENT.equals(node.get(PROPERTY_TYPE))) {
      return documentNode(node);
    }
    if (node.get(PROPERTY_TYPE) instanceof List<?> types && types.contains(TYPE_DOCUMENT)) {
      throw new IllegalArgumentException(
          "The type 'document' cannot be part of a type array. Use \"type\": \"document\" for a"
              + " document, or \"anyOf\" with {\"type\": \"document\"} as one alternative for a"
              + " document or another value.");
    }
    return new LinkedHashMap<>(node);
  }

  /**
   * The handle schema with the description of the node; the other authored entries follow it unless
   * the handle schema defines them.
   */
  private Map<String, Object> documentNode(Map<String, Object> node) {
    final var result =
        new LinkedHashMap<>(
            DocumentSchemas.document(
                node.get(PROPERTY_DESCRIPTION) instanceof String description ? description : null));
    node.forEach(result::putIfAbsent);
    return result;
  }

  // a schema node read as JSON is a JSON object, so its keys are strings
  @SuppressWarnings("unchecked")
  private static Map<String, Object> cast(Map<?, ?> map) {
    return (Map<String, Object>) map;
  }
}
