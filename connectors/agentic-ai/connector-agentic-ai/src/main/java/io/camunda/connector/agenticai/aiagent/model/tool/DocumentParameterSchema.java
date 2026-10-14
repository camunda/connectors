/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static io.camunda.connector.agenticai.common.JsonSchemaConstants.PROPERTY_ANYOF;
import static io.camunda.connector.agenticai.common.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.common.JsonSchemaConstants.PROPERTY_FORMAT;
import static io.camunda.connector.agenticai.common.JsonSchemaConstants.PROPERTY_ITEMS;
import static io.camunda.connector.agenticai.common.JsonSchemaConstants.PROPERTY_ONEOF;
import static io.camunda.connector.agenticai.common.JsonSchemaConstants.PROPERTY_PROPERTIES;
import static io.camunda.connector.agenticai.common.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.common.JsonSchemaConstants.TYPE_STRING;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Document parameters of a tool input schema. A parameter that carries a document is either
 *
 * <ul>
 *   <li>a <em>document parameter</em>: a string schema with the format {@link #FORMAT_DOCUMENT_ID},
 *       whose value is the id of a known document or its {@code <doc />} tag, or
 *   <li>a <em>union parameter</em>: a node with {@code anyOf} or {@code oneOf} alternatives, one of
 *       which is such a string schema and at least one other alternative exists (typically a plain
 *       string). Its value is a document only when it is exactly one {@code <doc />} tag; any other
 *       value is not a document.
 * </ul>
 *
 * Both are found at the top level of the parameters, as array {@code items} and recursively through
 * object {@code properties} and array {@code items}. The alternatives of a union are not searched
 * further: a document inside an object or array alternative is not a document parameter. The format
 * inside {@code $ref} does not make a document parameter.
 *
 * <p>The description of a document parameter ends with {@link #HINT}, the description of a union
 * parameter with {@link #UNION_HINT}, see {@link #withHint}. The stored schema keeps the format;
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
      "Pass the id of a known document (the id attribute of its <doc /> tag) or the tag itself.";

  /** Ends the description of every union parameter. */
  public static final String UNION_HINT =
      "This parameter takes text or a document. To pass a document, give its tag exactly as you"
          + " read it, for example <doc id=\"...\" />. Any other text is passed as text.";

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
   * @param union {@code true} for a union parameter (text or a document), {@code false} for a
   *     parameter that is always a document
   */
  public record DocumentParameter(List<Step> path, boolean union) {}

  /** Whether the input schema declares at least one document parameter or union parameter. */
  public static boolean hasDocumentParameters(Map<String, Object> inputSchema) {
    return !documentParameters(inputSchema).isEmpty();
  }

  /**
   * The document parameters and union parameters of the object schema of the tool arguments, in
   * schema order. The arguments of a tool call mirror the schema, so a path addresses the argument
   * values that carry a document id.
   */
  public static List<DocumentParameter> documentParameters(Map<String, Object> inputSchema) {
    final var parameters = new ArrayList<DocumentParameter>();
    collectParameters(inputSchema, List.of(), parameters);
    return parameters;
  }

  /**
   * Whether the schema node is a union parameter: it has {@code anyOf} or {@code oneOf}
   * alternatives, one of them a document schema (a string schema with the format {@link
   * #FORMAT_DOCUMENT_ID}) and at least one other.
   */
  public static boolean isUnionParameter(Map<?, ?> schema) {
    return unionKeyword(schema) != null;
  }

  /**
   * Returns the tool definitions with the schema the model providers get: every document parameter
   * is a string schema without the format, every union parameter has its document alternative
   * replaced by a plain string alternative (see {@link #toProviderSchema}), and the document
   * delivery is dropped. A definition without document parameters and without a document delivery
   * is returned as is; the others are copies, so no stored definition is modified.
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
   * parameter and {@link #UNION_HINT} at the end of the description of every union parameter, found
   * at the top level, as array {@code items} and through object {@code properties}. A description
   * that already contains the hint is kept, so applying this twice changes nothing; a schema
   * without document parameters is returned as is.
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> withHint(Map<String, Object> schema) {
    if (!containsDocument(schema)) {
      return schema;
    }
    final var result = new LinkedHashMap<>(schema);
    if (isDocument(result)) {
      putHint(result, HINT);
    } else if (isUnionParameter(result)) {
      putHint(result, UNION_HINT);
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

  private static void putHint(Map<String, Object> node, String hint) {
    final var description = node.get(PROPERTY_DESCRIPTION);
    if (!(description instanceof String text) || StringUtils.isBlank(text)) {
      node.put(PROPERTY_DESCRIPTION, hint);
    } else if (!text.contains(hint)) {
      node.put(PROPERTY_DESCRIPTION, appendHint(text, hint));
    }
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
   * level, as array {@code items}, through object {@code properties} and inside {@code anyOf} and
   * {@code oneOf} alternatives. A node whose {@code type} is an array that lists {@link
   * #TYPE_DOCUMENT} next to other types and that has no {@code anyOf} or {@code oneOf} becomes a
   * node with an {@code anyOf} of one alternative per type, in the place of the {@code type} entry;
   * the document alternative is the string schema with the format. The other entries of a node keep
   * their order and values.
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> normalizeAuthoredSchema(Map<String, Object> schema) {
    var result = new LinkedHashMap<>(schema);
    if (TYPE_DOCUMENT.equals(result.get(PROPERTY_TYPE))) {
      result.put(PROPERTY_TYPE, TYPE_STRING);
      result.put(PROPERTY_FORMAT, FORMAT_DOCUMENT_ID);
    } else if (result.get(PROPERTY_TYPE) instanceof List<?> types
        && types.contains(TYPE_DOCUMENT)
        && !result.containsKey(PROPERTY_ANYOF)
        && !result.containsKey(PROPERTY_ONEOF)) {
      result = typeArrayToAlternatives(result, types);
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
    for (final var keyword : List.of(PROPERTY_ANYOF, PROPERTY_ONEOF)) {
      if (result.get(keyword) instanceof List<?> alternatives) {
        final var normalized = new ArrayList<Object>();
        for (final var alternative : alternatives) {
          normalized.add(
              alternative instanceof Map<?, ?> alternativeSchema
                  ? normalizeAuthoredSchema((Map<String, Object>) alternativeSchema)
                  : alternative);
        }
        result.put(keyword, normalized);
      }
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static LinkedHashMap<String, Object> typeArrayToAlternatives(
      Map<String, Object> node, List<?> types) {
    final var alternatives = new ArrayList<Object>();
    for (final var type : types) {
      final var alternative = new LinkedHashMap<String, Object>();
      if (TYPE_DOCUMENT.equals(type)) {
        alternative.put(PROPERTY_TYPE, TYPE_STRING);
        alternative.put(PROPERTY_FORMAT, FORMAT_DOCUMENT_ID);
      } else {
        alternative.put(PROPERTY_TYPE, type);
      }
      alternatives.add(alternative);
    }

    final var result = new LinkedHashMap<String, Object>();
    node.forEach(
        (key, value) -> {
          if (PROPERTY_TYPE.equals(key)) {
            if (alternatives.size() == 1) {
              result.putAll((Map<String, Object>) alternatives.getFirst());
            } else {
              result.put(PROPERTY_ANYOF, alternatives);
            }
          } else {
            result.put(key, value);
          }
        });
    return result;
  }

  private static boolean isDocument(Map<?, ?> schema) {
    return FORMAT_DOCUMENT_ID.equals(schema.get(PROPERTY_FORMAT));
  }

  /**
   * The keyword ({@code anyOf} or {@code oneOf}) that makes the node a union parameter, or {@code
   * null}.
   */
  private static @Nullable String unionKeyword(Map<?, ?> schema) {
    if (isDocument(schema)) {
      return null;
    }
    for (final var keyword : List.of(PROPERTY_ANYOF, PROPERTY_ONEOF)) {
      if (schema.get(keyword) instanceof List<?> alternatives
          && alternatives.size() > 1
          && alternatives.stream()
              .anyMatch(alternative -> alternative instanceof Map<?, ?> map && isDocument(map))) {
        return keyword;
      }
    }
    return null;
  }

  private static void collectParameters(
      Object node, List<Step> path, List<DocumentParameter> parameters) {
    if (!(node instanceof Map<?, ?> schema)) {
      return;
    }
    if (isDocument(schema)) {
      if (!path.isEmpty()) {
        parameters.add(new DocumentParameter(path, false));
      }
    } else if (isUnionParameter(schema)) {
      if (!path.isEmpty()) {
        parameters.add(new DocumentParameter(path, true));
      }
    } else if (schema.get(PROPERTY_PROPERTIES) instanceof Map<?, ?> properties) {
      properties.forEach(
          (name, child) -> {
            final var childPath = new ArrayList<>(path);
            childPath.add(new Step.Property(String.valueOf(name)));
            collectParameters(child, Collections.unmodifiableList(childPath), parameters);
          });
    } else if (schema.get(PROPERTY_ITEMS) instanceof Map<?, ?> items) {
      final var childPath = new ArrayList<>(path);
      childPath.add(new Step.Items());
      collectParameters(items, Collections.unmodifiableList(childPath), parameters);
    }
  }

  private static boolean containsDocument(@Nullable Object node) {
    if (!(node instanceof Map<?, ?> schema)) {
      return false;
    }
    if (isDocument(schema) || isUnionParameter(schema)) {
      return true;
    }
    if (schema.get(PROPERTY_PROPERTIES) instanceof Map<?, ?> properties) {
      return properties.values().stream().anyMatch(DocumentParameterSchema::containsDocument);
    }
    return containsDocument(schema.get(PROPERTY_ITEMS));
  }

  /**
   * The schema the model providers get: a document parameter is a string schema without the format.
   * A union parameter has its document alternative replaced by a plain string alternative,
   * identical alternatives are merged, and a single remaining alternative replaces the {@code
   * anyOf} or {@code oneOf}: its entries take the place of the keyword in the node, next to the
   * entries of the node, which win (the description of the node is kept).
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> toProviderSchema(Map<String, Object> schema) {
    if (!containsDocument(schema)) {
      return schema;
    }
    if (isUnionParameter(schema)) {
      return toProviderUnion(schema);
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

  @SuppressWarnings("unchecked")
  private static Map<String, Object> toProviderUnion(Map<String, Object> schema) {
    final var keyword = Objects.requireNonNull(unionKeyword(schema));
    final var alternatives = new ArrayList<Object>();
    for (final var alternative : (List<Object>) Objects.requireNonNull(schema.get(keyword))) {
      final var provider =
          alternative instanceof Map<?, ?> map && isDocument(map)
              ? toProviderSchema((Map<String, Object>) map)
              : alternative;
      if (!alternatives.contains(provider)) {
        alternatives.add(provider);
      }
    }

    final var result = new LinkedHashMap<String, Object>();
    if (alternatives.size() == 1 && alternatives.getFirst() instanceof Map<?, ?> only) {
      schema.forEach(
          (key, value) -> {
            if (keyword.equals(key)) {
              only.forEach(
                  (onlyKey, onlyValue) -> {
                    if (!schema.containsKey(onlyKey)) {
                      result.put((String) onlyKey, onlyValue);
                    }
                  });
            } else {
              result.put(key, value);
            }
          });
    } else {
      result.putAll(schema);
      result.put(keyword, Collections.unmodifiableList(alternatives));
    }
    return Collections.unmodifiableMap(result);
  }
}
