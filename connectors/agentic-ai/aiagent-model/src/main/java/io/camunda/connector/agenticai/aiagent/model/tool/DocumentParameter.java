/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_ENUM;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_ITEMS;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_PROPERTIES;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_REQUIRED;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.TYPE_OBJECT;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.TYPE_STRING;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * A parameter of a tool that carries a document, found in the input schema of the {@link
 * ToolDefinition tool definition}. Its schema is the schema of an {@link AgentDocumentHandle} (see
 * {@link #documentSchema}), and the value the model passes is a handle.
 *
 * <p>A document parameter sits at the top level of the input schema, in the {@code items} of an
 * array or in the {@code properties} of an object, at any depth (see {@link #mapChildren}). The
 * {@link #path() path} leads from the input schema to it. For this input schema
 *
 * <pre>{@code
 * {
 *   "type": "object",
 *   "properties": {
 *     "report": <document schema>,
 *     "attachments": {"type": "array", "items": <document schema>},
 *     "message": {
 *       "type": "object",
 *       "properties": {"file": <document schema>, "caption": {"type": "string"}}
 *     }
 *   }
 * }
 * }</pre>
 *
 * the document parameters are {@code [Property(report)]}, {@code [Property(attachments), Items]}
 * and {@code [Property(message), Property(file)]}. A document schema anywhere else, such as in an
 * {@code anyOf} alternative or behind {@code $ref}, is no document parameter.
 *
 * @param path the steps from the input schema to the parameter. The arguments of a tool call mirror
 *     the schema, so the path addresses the argument values that carry a document.
 */
public record DocumentParameter(List<Step> path) {

  /** The type with which an authored schema declares a document parameter. */
  public static final String TYPE_DOCUMENT = "document";

  // keywords whose values are data or property names, not schemas
  private static final Set<String> DATA_KEYWORDS =
      Set.of("const", "enum", "default", "examples", "dependentRequired");

  // keywords whose values map names to schemas: a name is no keyword
  private static final Set<String> NAMED_SCHEMA_KEYWORDS =
      Set.of(
          "properties",
          "patternProperties",
          "dependentSchemas",
          "dependencies",
          "$defs",
          "definitions");

  public DocumentParameter {
    path = List.copyOf(path);
  }

  /** A step from a schema node to the schema of a nested value. */
  public sealed interface Step {

    /** The schema of the object property {@code name}. */
    record Property(String name) implements Step {}

    /** The schema of the elements of an array. */
    record Items() implements Step {}
  }

  /**
   * The document parameters of the input schema of a tool, in schema order. The input schema itself
   * is no parameter.
   */
  public static List<DocumentParameter> findAll(Map<String, Object> inputSchema) {
    final var parameters = new ArrayList<DocumentParameter>();
    mapChildren(
        inputSchema,
        (step, child) -> {
          collect(child, List.of(step), parameters);
          return child;
        });
    return List.copyOf(parameters);
  }

  /**
   * The input schema of a tool with each authored type {@value #TYPE_DOCUMENT} turned into the
   * {@link #documentSchema schema of a document parameter}. The description of the authored node
   * becomes the description of the document schema, and its other entries follow unless the
   * document schema defines them.
   *
   * <p>The type is allowed where a document parameter can sit: as the type of a parameter, of the
   * {@code items} of an array or of an object property, at any depth (see {@link #mapChildren}).
   * The values of {@code const}, {@code enum}, {@code default}, {@code examples} and {@code
   * dependentRequired} are data, so a type there is left as it is.
   *
   * @throws MisplacedDocumentTypeException if a parameter has the type anywhere else, such as in an
   *     {@code anyOf} alternative or in a {@code type} array: the model provider does not know it
   */
  public static Map<String, Object> fromDocumentType(Map<String, Object> inputSchema) {
    return mapChildren(
        inputSchema,
        (step, parameterSchema) -> {
          final var mapped = mapDocumentType(parameterSchema);
          if (containsDocumentType(mapped)) {
            throw new MisplacedDocumentTypeException(
                step instanceof Step.Property(var name) ? name : "items");
          }
          return mapped;
        });
  }

  /**
   * The schema of a document parameter: the object schema of an {@link AgentDocumentHandle}.
   *
   * <pre>{@code
   * {
   *   "type": "object",
   *   "description": "<description>",
   *   "properties": {
   *     "type": {"type": "string", "enum": ["camunda.agenticai.document"]},
   *     "id": {"type": "string", "description": "The id of the document."}
   *   },
   *   "required": ["type", "id"]
   * }
   * }</pre>
   *
   * @param description what the parameter is for, shown to the model; without a description, the
   *     schema has no {@code description}
   * @return an insertion-ordered map
   */
  public static Map<String, Object> documentSchema(@Nullable String description) {
    final var type = new LinkedHashMap<String, Object>();
    type.put(PROPERTY_TYPE, TYPE_STRING);
    type.put(PROPERTY_ENUM, List.of(AgentDocumentHandle.TYPE));

    final var id = new LinkedHashMap<String, Object>();
    id.put(PROPERTY_TYPE, TYPE_STRING);
    id.put(PROPERTY_DESCRIPTION, "The id of the document.");

    final var properties = new LinkedHashMap<String, Object>();
    properties.put(AgentDocumentHandle.FIELD_TYPE, type);
    properties.put(AgentDocumentHandle.FIELD_ID, id);

    final var schema = new LinkedHashMap<String, Object>();
    schema.put(PROPERTY_TYPE, TYPE_OBJECT);
    if (StringUtils.isNotBlank(description)) {
      schema.put(PROPERTY_DESCRIPTION, description);
    }
    schema.put(PROPERTY_PROPERTIES, properties);
    schema.put(
        PROPERTY_REQUIRED, List.of(AgentDocumentHandle.FIELD_TYPE, AgentDocumentHandle.FIELD_ID));
    return schema;
  }

  /**
   * Returns a copy of the schema node in which every child schema where a document parameter can
   * sit is replaced by what the mapper returns for it, given the step to the child. These are the
   * schemas of the object properties and, for a node without properties, the {@code items} schema.
   * The other entries keep their order and values.
   */
  public static Map<String, Object> mapChildren(
      Map<String, Object> node, BiFunction<Step, Map<String, Object>, Map<String, Object>> mapper) {
    final var result = new LinkedHashMap<>(node);
    if (result.get(PROPERTY_PROPERTIES) instanceof Map<?, ?> properties) {
      final var mapped = new LinkedHashMap<String, Object>();
      properties.forEach((name, child) ->
          mapped.put(
              String.valueOf(name),
              child instanceof Map<?, ?> childSchema
                  ? mapper.apply(new Step.Property(String.valueOf(name)), cast(childSchema))
                  : child));
      result.put(PROPERTY_PROPERTIES, Collections.unmodifiableMap(mapped));
    } else if (result.get(PROPERTY_ITEMS) instanceof Map<?, ?> items) {
      result.put(PROPERTY_ITEMS, mapper.apply(new Step.Items(), cast(items)));
    }
    return result;
  }

  private static Map<String, Object> mapDocumentType(Map<String, Object> node) {
    if (TYPE_DOCUMENT.equals(node.get(PROPERTY_TYPE))) {
      final var result =
          new LinkedHashMap<>(
              documentSchema(
                  node.get(PROPERTY_DESCRIPTION) instanceof String description
                      ? description
                      : null));
      node.forEach(result::putIfAbsent);
      return result;
    }
    return mapChildren(node, (step, child) -> mapDocumentType(child));
  }

  private static boolean containsDocumentType(@Nullable Object node) {
    if (node instanceof Map<?, ?> map) {
      final var type = map.get(PROPERTY_TYPE);
      if (TYPE_DOCUMENT.equals(type)
          || (type instanceof List<?> types && types.contains(TYPE_DOCUMENT))) {
        return true;
      }
      return map.entrySet().stream()
          .anyMatch(entry -> {
            final var keyword = String.valueOf(entry.getKey());
            if (DATA_KEYWORDS.contains(keyword)) {
              return false;
            }
            if (NAMED_SCHEMA_KEYWORDS.contains(keyword)
                && entry.getValue() instanceof Map<?, ?> named) {
              return named.values().stream().anyMatch(DocumentParameter::containsDocumentType);
            }
            return containsDocumentType(entry.getValue());
          });
    }
    if (node instanceof List<?> list) {
      return list.stream().anyMatch(DocumentParameter::containsDocumentType);
    }
    return false;
  }

  /**
   * Whether the schema node is the schema of a document handle (see {@link #documentSchema}): an
   * object whose {@code properties.type.enum} is exactly {@code ["camunda.agenticai.document"]}.
   */
  private static boolean isDocumentSchema(Map<?, ?> schema) {
    return TYPE_OBJECT.equals(schema.get(PROPERTY_TYPE))
        && schema.get(PROPERTY_PROPERTIES) instanceof Map<?, ?> properties
        && properties.get(AgentDocumentHandle.FIELD_TYPE) instanceof Map<?, ?> type
        && List.of(AgentDocumentHandle.TYPE).equals(type.get(PROPERTY_ENUM));
  }

  /**
   * Adds the document parameters at or below {@code node} to {@code parameters}, in schema order.
   * The search stops at a document schema, so its {@code type} and {@code id} are no parameters.
   */
  private static void collect(
      Map<String, Object> node, List<Step> path, List<DocumentParameter> parameters) {
    if (isDocumentSchema(node)) {
      parameters.add(new DocumentParameter(path));
      return;
    }
    mapChildren(
        node,
        (step, child) -> {
          final var childPath = new ArrayList<>(path);
          childPath.add(step);
          collect(child, childPath, parameters);
          return child;
        });
  }

  // a schema node read as JSON is a JSON object, so its keys are strings
  @SuppressWarnings("unchecked")
  private static Map<String, Object> cast(Map<?, ?> map) {
    return (Map<String, Object>) map;
  }
}
