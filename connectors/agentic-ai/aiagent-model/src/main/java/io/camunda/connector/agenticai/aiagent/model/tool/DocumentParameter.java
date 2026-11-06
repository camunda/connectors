/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_ANYOF;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_ENUM;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_ITEMS;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_ONEOF;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_PROPERTIES;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.TYPE_OBJECT;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;

/**
 * A parameter of a tool that carries a document, found in the input schema of the {@link
 * ToolDefinition tool definition}. It is either
 *
 * <ul>
 *   <li>a {@link Document}: the schema of a {@link AgentDocumentHandle}, an object whose {@code
 *       properties.type.enum} is {@code ["camunda.agent_document"]}, or
 *   <li>a {@link TextOrDocument}: a node with {@code anyOf} or {@code oneOf} alternatives, one of
 *       which is such a handle schema and at least one other alternative exists (a string, an
 *       object, ...). A string value of it is text; an object value with the handle {@code type} is
 *       a document.
 * </ul>
 *
 * Both are found at the top level of the schema of the tool arguments, as array {@code items} and
 * recursively through object {@code properties} and array {@code items} (see {@link
 * Step#mapChildren}). The search stops at a handle schema, so its {@code type} and {@code id}
 * properties are no parameters, and at a {@link TextOrDocument}: the alternatives of a union are
 * not searched further, because a value cannot be assigned to one of its alternatives reliably. A
 * handle schema behind {@code $ref} does not make a document parameter.
 */
public sealed interface DocumentParameter {

  /**
   * The steps from the object schema of the tool arguments to the parameter. The arguments of a
   * tool call mirror the schema, so the path addresses the argument values that carry a document.
   */
  List<Step> path();

  /** A parameter that is always a document. */
  record Document(List<Step> path) implements DocumentParameter {
    public Document {
      path = List.copyOf(path);
    }
  }

  /** A parameter that takes text or a document. */
  record TextOrDocument(List<Step> path) implements DocumentParameter {
    public TextOrDocument {
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
   * The document parameters of the object schema of the tool arguments, in schema order. A schema
   * that is itself a document is not a parameter of the arguments.
   */
  static List<DocumentParameter> in(Map<String, Object> inputSchema) {
    final var parameters = new ArrayList<DocumentParameter>();
    collect(inputSchema, List.of(), parameters);
    parameters.removeIf(parameter -> parameter.path().isEmpty());
    return List.copyOf(parameters);
  }

  /**
   * Whether the schema node is the schema of a document handle: an object whose {@code
   * properties.type.enum} is exactly the handle type.
   */
  static boolean isDocumentSchema(Map<?, ?> schema) {
    return TYPE_OBJECT.equals(schema.get(PROPERTY_TYPE))
        && schema.get(PROPERTY_PROPERTIES) instanceof Map<?, ?> properties
        && properties.get(PROPERTY_TYPE) instanceof Map<?, ?> type
        && List.of(AgentDocumentHandle.TYPE).equals(type.get(PROPERTY_ENUM));
  }

  /**
   * Whether the schema node has {@code anyOf} or {@code oneOf} alternatives, one of them a document
   * schema and at least one other, of any kind.
   */
  static boolean isTextOrDocumentSchema(Map<?, ?> schema) {
    return alternativesKeyword(schema) != null;
  }

  /**
   * The keyword ({@code anyOf} or {@code oneOf}) of the alternatives that make the node a {@link
   * TextOrDocument}, or {@code null} when the node is none.
   */
  static @Nullable String alternativesKeyword(Map<?, ?> schema) {
    if (isDocumentSchema(schema)) {
      return null;
    }
    for (final var keyword : List.of(PROPERTY_ANYOF, PROPERTY_ONEOF)) {
      if (schema.get(keyword) instanceof List<?> alternatives
          && alternatives.size() > 1
          && alternatives.stream()
              .anyMatch(
                  alternative -> alternative instanceof Map<?, ?> map && isDocumentSchema(map))) {
        return keyword;
      }
    }
    return null;
  }

  /**
   * Adds the document parameters found at or below {@code node} to {@code parameters}, in schema
   * order. A document or text-or-document node is not searched further.
   */
  private static void collect(
      Map<String, Object> node, List<Step> path, List<DocumentParameter> parameters) {
    if (isDocumentSchema(node)) {
      parameters.add(new Document(path));
    } else if (isTextOrDocumentSchema(node)) {
      parameters.add(new TextOrDocument(path));
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
