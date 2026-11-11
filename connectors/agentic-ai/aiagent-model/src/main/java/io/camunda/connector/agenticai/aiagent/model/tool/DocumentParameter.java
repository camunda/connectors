/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_ANYOF;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_ENUM;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_ITEMS;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_ONEOF;
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
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * A parameter of a tool that carries a document, found in the input schema of the {@link
 * ToolDefinition tool definition}. It is either
 *
 * <ul>
 *   <li>a {@link Document}: the schema of an {@link AgentDocumentHandle}, an object whose {@code
 *       properties.type.enum} is {@code ["camunda.agenticai.document"]}; the value must be a
 *       handle, or
 *   <li>a {@link Union}: a node with {@code anyOf} or {@code oneOf} alternatives (see {@link
 *       #mapAlternatives}), a handle schema sitting anywhere inside them: directly, in the {@code
 *       properties} of an object alternative, in the {@code items} of an array alternative or in a
 *       nested {@code anyOf} or {@code oneOf}. Every handle at or below the value is a document,
 *       everything else in the value is no document.
 * </ul>
 *
 * Both are found at the top level of the schema of the tool arguments, as array {@code items} and
 * recursively through object {@code properties} and array {@code items} (see {@link
 * Step#mapChildren}). The search stops at a handle schema, so its {@code type} and {@code id}
 * properties are no parameters, and at a {@link Union}: inside a union the agent scans the value
 * for handles, not the schema, because a handle identifies itself by its {@code type}. A handle
 * schema behind {@code $ref} does not make a document parameter.
 */
public sealed interface DocumentParameter {

  /**
   * The steps from the object schema of the tool arguments to the parameter. The arguments of a
   * tool call mirror the schema, so the path addresses the argument values that carry a document.
   */
  List<Step> path();

  /** A parameter whose value must be the handle of a document. */
  record Document(List<Step> path) implements DocumentParameter {
    public Document {
      path = List.copyOf(path);
    }
  }

  /**
   * A parameter whose value may hold documents next to other values: every handle at or below the
   * value, also inside objects and arrays, is a document; text, numbers, booleans and {@code null}
   * stay as they are, and so does the rest of an object or an array around its handles.
   */
  record Union(List<Step> path) implements DocumentParameter {
    public Union {
      path = List.copyOf(path);
    }
  }

  /** A step from a schema node to the schema of a nested value. */
  sealed interface Step {

    /** The schema of the object property {@code name}. */
    record Property(String name) implements Step {}

    /** The schema of the elements of an array. */
    record Items() implements Step {}

    /**
     * Returns a copy of the schema node in which every child schema, where a parameter of the tool
     * arguments can sit, is replaced by what the mapper returns for it, given the step to the
     * child. These are the schemas of the object properties and, for a node without properties, the
     * {@code items} schema. The other entries keep their order and values. This is the one place
     * that defines where a document parameter can be.
     */
    // a child of a schema node read as JSON is a JSON object, so its keys are strings
    @SuppressWarnings("unchecked")
    static Map<String, Object> mapChildren(
        Map<String, Object> node,
        BiFunction<Step, Map<String, Object>, Map<String, Object>> mapper) {
      final var result = new LinkedHashMap<>(node);
      if (result.get(PROPERTY_PROPERTIES) instanceof Map<?, ?> properties) {
        final var mapped = new LinkedHashMap<String, Object>();
        properties.forEach((name, child) ->
            mapped.put(
                String.valueOf(name),
                child instanceof Map<?, ?> childSchema
                    ? mapper.apply(
                        new Property(String.valueOf(name)), (Map<String, Object>) childSchema)
                    : child));
        result.put(PROPERTY_PROPERTIES, Collections.unmodifiableMap(mapped));
      } else if (result.get(PROPERTY_ITEMS) instanceof Map<?, ?> items) {
        result.put(PROPERTY_ITEMS, mapper.apply(new Items(), (Map<String, Object>) items));
      }
      return result;
    }
  }

  /**
   * The document parameters of the object schema of the tool arguments, in schema order. The search
   * starts at the children of the schema: the schema itself is no parameter, also when it is a
   * document or has {@code anyOf} or {@code oneOf} alternatives next to its properties.
   */
  static List<DocumentParameter> in(Map<String, Object> inputSchema) {
    final var parameters = new ArrayList<DocumentParameter>();
    Step.mapChildren(
        inputSchema,
        (step, child) -> {
          collect(child, List.of(step), parameters);
          return child;
        });
    return List.copyOf(parameters);
  }

  /**
   * The schema of a parameter that is always a document: the object schema of an {@link
   * AgentDocumentHandle}, with the properties {@code type} (the handle type) and {@code id}, both
   * required. {@link #in} recognizes it as a {@link Document}.
   *
   * @param description what the parameter is for, shown to the model, or {@code null}
   * @return an insertion-ordered map
   */
  static Map<String, Object> documentSchema(@Nullable String description) {
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
   * Returns a copy of the schema node in which every {@code anyOf} and {@code oneOf} alternative
   * that is a schema node is replaced by what the mapper returns for it. The other entries keep
   * their order and values. This is the one place that defines the alternatives of a union.
   */
  static Map<String, Object> mapAlternatives(
      Map<String, Object> node, UnaryOperator<Map<String, Object>> mapper) {
    final var result = new LinkedHashMap<>(node);
    for (final var keyword : List.of(PROPERTY_ANYOF, PROPERTY_ONEOF)) {
      if (result.get(keyword) instanceof List<?> alternatives) {
        final var mapped = new ArrayList<Object>();
        for (final var alternative : alternatives) {
          mapped.add(alternative instanceof Map<?, ?> map ? mapper.apply(cast(map)) : alternative);
        }
        result.put(keyword, Collections.unmodifiableList(mapped));
      }
    }
    return result;
  }

  /**
   * Whether the schema node is the schema of a document handle (see {@link #documentSchema}): an
   * object whose {@code properties.type.enum} is exactly the handle type.
   */
  private static boolean isDocumentSchema(Map<?, ?> schema) {
    return TYPE_OBJECT.equals(schema.get(PROPERTY_TYPE))
        && schema.get(PROPERTY_PROPERTIES) instanceof Map<?, ?> properties
        && properties.get(AgentDocumentHandle.FIELD_TYPE) instanceof Map<?, ?> type
        && List.of(AgentDocumentHandle.TYPE).equals(type.get(PROPERTY_ENUM));
  }

  /**
   * Whether the schema node is a union: it is no document schema, and a document schema sits inside
   * one of its {@code anyOf} or {@code oneOf} alternatives (see {@link #containsDocumentSchema}).
   */
  static boolean isUnionSchema(Map<?, ?> schema) {
    return !isDocumentSchema(schema)
        && alternatives(cast(schema)).stream().anyMatch(DocumentParameter::containsDocumentSchema);
  }

  /**
   * Whether the schema node is a document schema or has one below it: in its alternatives, or where
   * a document parameter can sit (see {@link Step#mapChildren}).
   */
  private static boolean containsDocumentSchema(Map<String, Object> schema) {
    return isDocumentSchema(schema)
        || alternatives(schema).stream().anyMatch(DocumentParameter::containsDocumentSchema)
        || !in(schema).isEmpty();
  }

  /** The alternatives of the node that are schema nodes (see {@link #mapAlternatives}). */
  private static List<Map<String, Object>> alternatives(Map<String, Object> node) {
    final var alternatives = new ArrayList<Map<String, Object>>();
    mapAlternatives(
        node,
        alternative -> {
          alternatives.add(alternative);
          return alternative;
        });
    return alternatives;
  }

  // a schema node read as JSON is a JSON object, so its keys are strings
  @SuppressWarnings("unchecked")
  private static Map<String, Object> cast(Map<?, ?> map) {
    return (Map<String, Object>) map;
  }

  /**
   * Adds the document parameters found at or below {@code node} to {@code parameters}, in schema
   * order. A document or union node is not searched further.
   */
  private static void collect(
      Map<String, Object> node, List<Step> path, List<DocumentParameter> parameters) {
    if (isDocumentSchema(node)) {
      parameters.add(new Document(path));
    } else if (isUnionSchema(node)) {
      parameters.add(new Union(path));
    } else {
      Step.mapChildren(
          node,
          (step, child) -> {
            final var childPath = new ArrayList<>(path);
            childPath.add(step);
            collect(child, childPath, parameters);
            return child;
          });
    }
  }
}
