/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static io.camunda.connector.agenticai.common.JsonSchemaConstants.PROPERTY_DESCRIPTION;
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
 * Document parameters of a tool input schema. A parameter that carries a document reference has the
 * schema type {@code document}; it is found at the top level of the parameters, as array {@code
 * items} and recursively through object {@code properties} and array {@code items}. {@code
 * document} inside {@code anyOf}, {@code oneOf} or {@code $ref} is not a document parameter.
 *
 * <p>The authored schema keeps the type {@code document}. The model providers only know JSON schema
 * types, so {@link #toProviderToolDefinitions} derives the schema they get: every {@code document}
 * node becomes a string schema whose description ends with {@link #PROVIDER_HINT}.
 */
public final class DocumentParameterSchema {

  /** Schema type which marks a parameter as a reference to a known document. */
  public static final String TYPE_DOCUMENT = "document";

  /** Appended to the description of a document parameter in the schema the model providers get. */
  public static final String PROVIDER_HINT = "Pass the id of a <doc/> tag of a known document.";

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
   * is a string schema carrying {@link #PROVIDER_HINT}. A definition without document parameters is
   * returned as is; the others are deep copies, so no authored definition is modified.
   */
  public static List<ToolDefinition> toProviderToolDefinitions(
      List<ToolDefinition> toolDefinitions) {
    return toolDefinitions.stream().map(DocumentParameterSchema::toProviderToolDefinition).toList();
  }

  private static ToolDefinition toProviderToolDefinition(ToolDefinition toolDefinition) {
    if (!containsDocument(toolDefinition.inputSchema())) {
      return toolDefinition;
    }
    // the canonical constructor keeps the order of the schema, which is part of the request bytes
    return new ToolDefinition(
        toolDefinition.name(),
        toolDefinition.description(),
        toProviderSchema(toolDefinition.inputSchema()));
  }

  private static void collectPaths(Object node, List<Step> path, List<List<Step>> paths) {
    if (!(node instanceof Map<?, ?> schema)) {
      return;
    }
    if (TYPE_DOCUMENT.equals(schema.get(PROPERTY_TYPE))) {
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
    if (TYPE_DOCUMENT.equals(schema.get(PROPERTY_TYPE))) {
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
    if (TYPE_DOCUMENT.equals(result.get(PROPERTY_TYPE))) {
      result.put(PROPERTY_TYPE, TYPE_STRING);
      final var description = result.get(PROPERTY_DESCRIPTION);
      result.put(
          PROPERTY_DESCRIPTION,
          description instanceof String text && StringUtils.isNotBlank(text)
              ? text + " " + PROVIDER_HINT
              : PROVIDER_HINT);
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
