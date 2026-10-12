/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static io.camunda.connector.agenticai.common.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.common.JsonSchemaConstants.PROPERTY_FORMAT;
import static io.camunda.connector.agenticai.common.JsonSchemaConstants.PROPERTY_ITEMS;
import static io.camunda.connector.agenticai.common.JsonSchemaConstants.PROPERTY_PROPERTIES;
import static io.camunda.connector.agenticai.common.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.common.JsonSchemaConstants.TYPE_STRING;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Document parameters of a tool input schema. A parameter that carries a document is a string
 * schema with the format {@link #FORMAT_DOCUMENT_ID}; it is found at the top level of the
 * parameters, as array {@code items} and recursively through object {@code properties} and array
 * {@code items}. The format inside {@code anyOf}, {@code oneOf} or {@code $ref} does not make a
 * document parameter.
 *
 * <p>The model passes the id of a known document as the value. The description of a document
 * parameter ends with {@link #HINT}, see {@link #withHint}. The stored schema keeps the format;
 * some model providers reject unknown formats, so {@link #toProviderToolDefinitions} derives the
 * schema they get by dropping the format.
 */
public final class DocumentParameterSchema {

  /** Schema format which marks a string parameter as the id of a known document. */
  public static final String FORMAT_DOCUMENT_ID = "camunda-document-id";

  /**
   * Type that fromAi expressions and authored schemas use as a shorthand for a document parameter;
   * {@link #normalizeAuthoredSchema} turns it into the string schema with {@link
   * #FORMAT_DOCUMENT_ID}.
   */
  public static final String TYPE_DOCUMENT = "document";

  /** Ends the description of every document parameter. */
  public static final String HINT =
      "Pass the id of a known document (the id attribute of its <doc /> tag).";

  private DocumentParameterSchema() {}

  /** A step from a schema node to the schema of a nested value. */
  public sealed interface Step {

    /** The schema of the object property {@code name}. */
    record Property(String name) implements Step {}

    /** The schema of the elements of an array. */
    record Items() implements Step {}
  }

  /** Whether the input schema declares at least one document parameter. */
  public static boolean hasDocumentParameters(Map<String, Object> inputSchema) {
    return !documentParameterPaths(inputSchema).isEmpty();
  }

  /**
   * The paths from the object schema of the tool arguments to every document parameter, in schema
   * order. The arguments of a tool call mirror the schema, so a path addresses the argument values
   * that carry a document id.
   */
  public static List<List<Step>> documentParameterPaths(Map<String, Object> inputSchema) {
    final var paths = new ArrayList<List<Step>>();
    collectPaths(inputSchema, List.of(), paths);
    return paths;
  }

  /**
   * Returns the tool definitions with the schema the model providers get: every document parameter
   * is a string schema without the format, and without the document delivery. A definition without
   * document parameters and without a document delivery is returned as is; the others are copies,
   * so no stored definition is modified.
   */
  public static List<ToolDefinition> toProviderToolDefinitions(
      List<ToolDefinition> toolDefinitions) {
    return toolDefinitions.stream().map(DocumentParameterSchema::toProviderToolDefinition).toList();
  }

  private static ToolDefinition toProviderToolDefinition(ToolDefinition toolDefinition) {
    if (!containsDocument(toolDefinition.inputSchema())) {
      return toolDefinition.documentDelivery() == null
          ? toolDefinition
          : toolDefinition.withDocumentDelivery(null);
    }
    // the canonical constructor keeps the order of the schema, which is part of the request bytes;
    // the document delivery is not part of what the provider gets
    return new ToolDefinition(
        toolDefinition.name(),
        toolDefinition.description(),
        toProviderSchema(toolDefinition.inputSchema()));
  }

  /**
   * Returns the input schema with {@link #HINT} at the end of the description of every document
   * parameter, found at the top level, as array {@code items} and through object {@code
   * properties}. A description that already contains the hint is kept, so applying this twice
   * changes nothing; a schema without document parameters is returned as is.
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> withHint(Map<String, Object> schema) {
    if (!containsDocument(schema)) {
      return schema;
    }
    final var result = new LinkedHashMap<>(schema);
    if (isDocument(result)) {
      final var description = result.get(PROPERTY_DESCRIPTION);
      if (!(description instanceof String text) || StringUtils.isBlank(text)) {
        result.put(PROPERTY_DESCRIPTION, HINT);
      } else if (!text.contains(HINT)) {
        result.put(PROPERTY_DESCRIPTION, text + " " + HINT);
      }
    } else if (result.get(PROPERTY_PROPERTIES) instanceof Map<?, ?> properties) {
      final var mapped = new LinkedHashMap<String, Object>();
      ((Map<String, Object>) properties)
          .forEach(
              (name, child) ->
                  mapped.put(
                      name,
                      child instanceof Map<?, ?> childSchema
                          ? withHint((Map<String, Object>) childSchema)
                          : child));
      result.put(PROPERTY_PROPERTIES, Collections.unmodifiableMap(mapped));
    } else if (result.get(PROPERTY_ITEMS) instanceof Map<?, ?> items) {
      result.put(PROPERTY_ITEMS, withHint((Map<String, Object>) items));
    }
    return Collections.unmodifiableMap(result);
  }

  /** The schema of a document parameter: a string with the format {@link #FORMAT_DOCUMENT_ID}. */
  public static Map<String, Object> documentSchema(@Nullable String description) {
    final var schema = new LinkedHashMap<String, Object>();
    schema.put(PROPERTY_TYPE, TYPE_STRING);
    schema.put(PROPERTY_FORMAT, FORMAT_DOCUMENT_ID);
    if (StringUtils.isNotBlank(description)) {
      schema.put(PROPERTY_DESCRIPTION, description);
    }
    return schema;
  }

  /**
   * Returns the parameter schema with every node of the shorthand type {@link #TYPE_DOCUMENT}
   * replaced by the string schema with the format {@link #FORMAT_DOCUMENT_ID}, found at the top
   * level, as array {@code items} and through object {@code properties}. The other entries of a
   * node keep their order and values.
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> normalizeAuthoredSchema(Map<String, Object> schema) {
    final var result = new LinkedHashMap<>(schema);
    if (TYPE_DOCUMENT.equals(result.get(PROPERTY_TYPE))) {
      result.put(PROPERTY_TYPE, TYPE_STRING);
      result.put(PROPERTY_FORMAT, FORMAT_DOCUMENT_ID);
    }
    if (result.get(PROPERTY_PROPERTIES) instanceof Map<?, ?> properties) {
      final var normalized = new LinkedHashMap<String, Object>();
      ((Map<String, Object>) properties)
          .forEach(
              (name, child) ->
                  normalized.put(
                      name,
                      child instanceof Map<?, ?> childSchema
                          ? normalizeAuthoredSchema((Map<String, Object>) childSchema)
                          : child));
      result.put(PROPERTY_PROPERTIES, normalized);
    }
    if (result.get(PROPERTY_ITEMS) instanceof Map<?, ?> items) {
      result.put(PROPERTY_ITEMS, normalizeAuthoredSchema((Map<String, Object>) items));
    }
    return result;
  }

  private static boolean isDocument(Map<?, ?> schema) {
    return FORMAT_DOCUMENT_ID.equals(schema.get(PROPERTY_FORMAT));
  }

  private static void collectPaths(Object node, List<Step> path, List<List<Step>> paths) {
    if (!(node instanceof Map<?, ?> schema)) {
      return;
    }
    if (isDocument(schema)) {
      if (!path.isEmpty()) {
        paths.add(path);
      }
    } else if (schema.get(PROPERTY_PROPERTIES) instanceof Map<?, ?> properties) {
      properties.forEach(
          (name, child) -> {
            final var childPath = new ArrayList<>(path);
            childPath.add(new Step.Property(String.valueOf(name)));
            collectPaths(child, Collections.unmodifiableList(childPath), paths);
          });
    } else if (schema.get(PROPERTY_ITEMS) instanceof Map<?, ?> items) {
      final var childPath = new ArrayList<>(path);
      childPath.add(new Step.Items());
      collectPaths(items, Collections.unmodifiableList(childPath), paths);
    }
  }

  private static boolean containsDocument(@Nullable Object node) {
    if (!(node instanceof Map<?, ?> schema)) {
      return false;
    }
    if (isDocument(schema)) {
      return true;
    }
    if (schema.get(PROPERTY_PROPERTIES) instanceof Map<?, ?> properties) {
      return properties.values().stream().anyMatch(DocumentParameterSchema::containsDocument);
    }
    return containsDocument(schema.get(PROPERTY_ITEMS));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> toProviderSchema(Map<String, Object> schema) {
    if (!containsDocument(schema)) {
      return schema;
    }
    final var result = new LinkedHashMap<>(schema);
    if (isDocument(result)) {
      result.remove(PROPERTY_FORMAT);
      result.put(PROPERTY_TYPE, TYPE_STRING);
    } else if (result.get(PROPERTY_PROPERTIES) instanceof Map<?, ?> properties) {
      final var mapped = new LinkedHashMap<String, Object>();
      ((Map<String, Object>) properties)
          .forEach(
              (name, child) ->
                  mapped.put(
                      name,
                      child instanceof Map<?, ?> childSchema
                          ? toProviderSchema((Map<String, Object>) childSchema)
                          : child));
      result.put(PROPERTY_PROPERTIES, Collections.unmodifiableMap(mapped));
    } else if (result.get(PROPERTY_ITEMS) instanceof Map<?, ?> items) {
      result.put(PROPERTY_ITEMS, toProviderSchema((Map<String, Object>) items));
    }
    return Collections.unmodifiableMap(result);
  }
}
