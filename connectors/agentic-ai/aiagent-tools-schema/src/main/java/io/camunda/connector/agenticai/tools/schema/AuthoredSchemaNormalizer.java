/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.tools.schema;

import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_TYPE;

import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Turns the shorthand type {@value #TYPE_DOCUMENT} that an authored {@code fromAi} schema uses for
 * a document into the schema of a {@link DocumentParameter document parameter} (see {@link
 * DocumentParameter#documentSchema}).
 *
 * <p>The shorthand is supported where a document parameter can sit: as the type of the parameter,
 * of the {@code items} of an array or of an object property, at any depth (see {@link
 * DocumentParameter#mapChildren}). Anywhere else, such as in an {@code anyOf} alternative or in a
 * {@code type} array, the schema is rejected: the model provider does not know the type.
 */
final class AuthoredSchemaNormalizer {

  /** Type that an authored schema uses as a shorthand for a document parameter. */
  static final String TYPE_DOCUMENT = "document";

  // keywords whose values are data, not schemas
  private static final Set<String> DATA_KEYWORDS = Set.of("const", "enum", "default", "examples");

  /**
   * The schema with the shorthand turned into the document schema.
   *
   * @throws IllegalArgumentException if the shorthand is used where no document parameter can sit
   */
  Map<String, Object> normalize(Map<String, Object> schema) {
    final var normalized = normalizeNode(schema);
    rejectRemainingShorthand(normalized);
    return normalized;
  }

  private Map<String, Object> normalizeNode(Map<String, Object> node) {
    if (TYPE_DOCUMENT.equals(node.get(PROPERTY_TYPE))) {
      return documentNode(node);
    }
    return DocumentParameter.mapChildren(node, (step, child) -> normalizeNode(child));
  }

  /**
   * The document schema with the description of the node; the other authored entries follow it
   * unless the document schema defines them.
   */
  private Map<String, Object> documentNode(Map<String, Object> node) {
    final var result =
        new LinkedHashMap<>(
            DocumentParameter.documentSchema(
                node.get(PROPERTY_DESCRIPTION) instanceof String description ? description : null));
    node.forEach(result::putIfAbsent);
    return result;
  }

  private void rejectRemainingShorthand(@Nullable Object node) {
    if (node instanceof Map<?, ?> map) {
      final var type = map.get(PROPERTY_TYPE);
      if (TYPE_DOCUMENT.equals(type)
          || (type instanceof List<?> types && types.contains(TYPE_DOCUMENT))) {
        throw new IllegalArgumentException(
            "The type 'document' can only be the type of a parameter, of the items of an array or"
                + " of an object property. Use a separate parameter for a document.");
      }
      map.forEach((key, child) -> {
        if (!DATA_KEYWORDS.contains(String.valueOf(key))) {
          rejectRemainingShorthand(child);
        }
      });
    } else if (node instanceof List<?> list) {
      list.forEach(this::rejectRemainingShorthand);
    }
  }
}
