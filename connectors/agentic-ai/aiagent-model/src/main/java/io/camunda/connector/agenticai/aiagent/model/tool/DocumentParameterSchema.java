/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_FORMAT;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_ITEMS;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_PROPERTIES;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.TYPE_STRING;

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
 * Document parameters of a tool input schema. A document parameter is a string schema with the
 * format {@link #FORMAT_DOCUMENT_ID}, whose value is the {@code <doc />} tag of a known document.
 * It is found at the top level of the parameters, as array {@code items} and recursively through
 * object {@code properties} and array {@code items}. The format inside {@code $ref} does not make a
 * document parameter.
 *
 * <p>The stored schema keeps the format and the description as authored. The model sees the
 * description of a document parameter ending with {@link #HINT}; some model providers reject
 * unknown formats, so {@link #toProviderToolDefinitions} derives the schema they get by adding the
 * hint and dropping the format.
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
      "Pass the tag of a known document exactly as you read it, for example <doc id=\"...\" />.";

  private DocumentParameterSchema() {}

  /** A step from a schema node to the schema of a nested value. */
  public sealed interface Step {

    /** The schema of the object property {@code name}. */
    record Property(String name) implements Step {}

    /** The schema of the elements of an array. */
    record Items() implements Step {}
  }

  /**
   * A document parameter of the tool arguments.
   *
   * @param path the steps from the object schema of the tool arguments to the parameter
   */
  public record DocumentParameter(List<Step> path) {}

  /** Whether the input schema declares at least one document parameter. */
  public static boolean hasDocumentParameters(Map<String, Object> inputSchema) {
    return !documentParameters(inputSchema).isEmpty();
  }

  /**
   * The document parameters of the object schema of the tool arguments, in schema order. The
   * arguments of a tool call mirror the schema, so a path addresses the argument values that carry
   * a document id.
   */
  public static List<DocumentParameter> documentParameters(Map<String, Object> inputSchema) {
    final var parameters = new ArrayList<DocumentParameter>();
    collectDocumentNodes(inputSchema, List.of(), parameters);
    // a schema that is itself a document is not a parameter of the arguments
    parameters.removeIf(parameter -> parameter.path().isEmpty());
    return parameters;
  }

  /**
   * Returns the tool definitions with the schema the model providers get: the description of every
   * document parameter ends with {@link #HINT} and every document parameter is a string schema
   * without the format. A definition without document parameters is returned as is; the others are
   * copies, so no stored definition is modified.
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
        toProviderSchema(withHint(toolDefinition.inputSchema())));
  }

  /**
   * Returns the input schema with {@link #HINT} at the end of the description of every document
   * parameter, found at the top level, as array {@code items} and through object {@code
   * properties}. A description that already contains the hint is kept, so applying this twice
   * changes nothing; a schema without document parameters is returned as is.
   */
  static Map<String, Object> withHint(Map<String, Object> schema) {
    return mapDocumentNodes(schema, node -> putHint(node, HINT));
  }

  private static Map<String, Object> putHint(Map<String, Object> node, String hint) {
    final var description = node.get(PROPERTY_DESCRIPTION);
    if (!(description instanceof String text) || StringUtils.isBlank(text)) {
      node.put(PROPERTY_DESCRIPTION, hint);
    } else if (!text.contains(hint)) {
      node.put(PROPERTY_DESCRIPTION, appendHint(text, hint));
    }
    return node;
  }

  /**
   * The description followed by the hint as a sentence of its own: a description that does not end
   * in sentence punctuation gets a period first.
   */
  private static String appendHint(String description, String hint) {
    final var trimmed = description.stripTrailing();
    final var last = trimmed.charAt(trimmed.length() - 1);
    final var separator = last == '.' || last == '!' || last == '?' ? " " : ". ";
    return trimmed + separator + hint;
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
  public static Map<String, Object> normalizeAuthoredSchema(Map<String, Object> schema) {
    final var result = new LinkedHashMap<>(schema);
    if (TYPE_DOCUMENT.equals(result.get(PROPERTY_TYPE))) {
      result.put(PROPERTY_TYPE, TYPE_STRING);
      result.put(PROPERTY_FORMAT, FORMAT_DOCUMENT_ID);
    }
    return mapChildren(result, Descent.ALL, (child, step) -> normalizeAuthoredSchema(child));
  }

  private static boolean isDocument(Map<?, ?> schema) {
    return FORMAT_DOCUMENT_ID.equals(schema.get(PROPERTY_FORMAT));
  }

  /** Which child schemas of a schema node {@link #mapChildren} descends into. */
  private enum Descent {
    /**
     * The schemas of the object properties, or without properties the items schema: where a
     * parameter of the tool arguments can sit.
     */
    PARAMETERS,
    /** Also the items next to properties. */
    ALL
  }

  /**
   * Returns a copy of the schema node in which every child schema is replaced by what the mapper
   * returns for it, given the step to the child. The other entries keep their order and values, and
   * so do the entries of the children that are not schemas.
   */
  @SuppressWarnings("unchecked")
  private static LinkedHashMap<String, Object> mapChildren(
      Map<String, Object> node,
      Descent descent,
      BiFunction<Map<String, Object>, Step, Map<String, Object>> mapper) {
    final var result = new LinkedHashMap<>(node);
    final var hasProperties = result.get(PROPERTY_PROPERTIES) instanceof Map<?, ?>;
    if (result.get(PROPERTY_PROPERTIES) instanceof Map<?, ?> properties) {
      final var mapped = new LinkedHashMap<String, Object>();
      properties.forEach(
          (name, child) ->
              mapped.put(
                  String.valueOf(name),
                  child instanceof Map<?, ?> childSchema
                      ? mapper.apply(
                          (Map<String, Object>) childSchema,
                          new Step.Property(String.valueOf(name)))
                      : child));
      result.put(PROPERTY_PROPERTIES, Collections.unmodifiableMap(mapped));
    }
    if ((descent == Descent.ALL || !hasProperties)
        && result.get(PROPERTY_ITEMS) instanceof Map<?, ?> items) {
      result.put(PROPERTY_ITEMS, mapper.apply((Map<String, Object>) items, new Step.Items()));
    }
    return result;
  }

  /**
   * Adds the document node found at or below {@code node} to {@code parameters}, in schema order. A
   * document node is not searched further.
   */
  private static void collectDocumentNodes(
      Map<String, Object> node, List<Step> path, List<DocumentParameter> parameters) {
    if (isDocument(node)) {
      parameters.add(new DocumentParameter(path));
    } else {
      mapChildren(
          node,
          Descent.PARAMETERS,
          (child, step) -> {
            final var childPath = new ArrayList<>(path);
            childPath.add(step);
            collectDocumentNodes(child, Collections.unmodifiableList(childPath), parameters);
            return child;
          });
    }
  }

  private static boolean containsDocument(Map<String, Object> schema) {
    final var nodes = new ArrayList<DocumentParameter>();
    collectDocumentNodes(schema, List.of(), nodes);
    return !nodes.isEmpty();
  }

  /**
   * Returns the schema with every document node replaced by what the function returns for a copy of
   * it; the nodes between them are copies with the replaced children, found through the properties
   * and the items. A schema without document nodes is returned as is.
   */
  private static Map<String, Object> mapDocumentNodes(
      Map<String, Object> schema, UnaryOperator<Map<String, Object>> document) {
    if (!containsDocument(schema)) {
      return schema;
    }
    if (isDocument(schema)) {
      return Collections.unmodifiableMap(document.apply(new LinkedHashMap<>(schema)));
    }
    return Collections.unmodifiableMap(
        mapChildren(
            schema, Descent.PARAMETERS, (child, step) -> mapDocumentNodes(child, document)));
  }

  /**
   * The schema the model providers get: a document parameter is a string schema without the format.
   */
  private static Map<String, Object> toProviderSchema(Map<String, Object> schema) {
    return mapDocumentNodes(
        schema,
        node -> {
          node.remove(PROPERTY_FORMAT);
          node.put(PROPERTY_TYPE, TYPE_STRING);
          return node;
        });
  }
}
