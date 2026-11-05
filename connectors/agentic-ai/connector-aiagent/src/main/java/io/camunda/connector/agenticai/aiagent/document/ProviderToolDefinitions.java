/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_FORMAT;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.TYPE_STRING;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter.Step;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * The tool definitions the model providers get. The stored definition keeps the schema of a {@link
 * DocumentParameter document parameter} as authored, with the format {@link
 * DocumentParameter#FORMAT_DOCUMENT_ID} and without a hint. The model sees the description of a
 * {@link DocumentParameter.Document} ending with {@link #HINT} and the description of a {@link
 * DocumentParameter.TextOrDocument} ending with {@link #UNION_HINT}; some model providers reject
 * unknown formats, so the schema they get has no format:
 *
 * <ul>
 *   <li>a document parameter is a plain string schema
 *   <li>a text-or-document parameter has its document alternative replaced by a plain string
 *       alternative, identical alternatives are merged, and a single remaining alternative replaces
 *       the {@code anyOf} or {@code oneOf}: its entries take the place of the keyword in the node,
 *       next to the entries of the node, which win (the description of the node is kept)
 * </ul>
 *
 * A definition without document parameters is returned as is; the others are copies with the order
 * of the schema kept, so no stored definition is modified. An agent instance that does not track
 * its documents has no document parameters: its definitions are sent as they are.
 */
public final class ProviderToolDefinitions {

  /** Ends the description of every document parameter. */
  public static final String HINT =
      "Pass the tag of a known document exactly as you read it, for example <doc id=\"...\" />.";

  /** Ends the description of every text-or-document parameter. */
  public static final String UNION_HINT =
      "This parameter takes text or a document. To pass a document, give its tag exactly as you"
          + " read it, for example <doc id=\"...\" />. Any other text is passed as text.";

  private ProviderToolDefinitions() {}

  public static List<ToolDefinition> of(
      List<ToolDefinition> toolDefinitions, DocumentHandling documentHandling) {
    return switch (documentHandling) {
      case DocumentHandling.Untracked ignored -> toolDefinitions;
      case DocumentHandling.Tracked ignored ->
          toolDefinitions.stream().map(ProviderToolDefinitions::of).toList();
    };
  }

  private static ToolDefinition of(ToolDefinition toolDefinition) {
    final var parameters = toolDefinition.documentParameters();
    if (parameters.isEmpty()) {
      return toolDefinition;
    }
    var schema = toolDefinition.inputSchema();
    for (final var parameter : parameters) {
      schema = rewrite(schema, parameter, 0);
    }
    // the canonical constructor keeps the order of the schema, which is part of the request bytes
    return new ToolDefinition(
        toolDefinition.name(), toolDefinition.description(), schema, toolDefinition.kind());
  }

  /** The schema with the node at the path of the parameter, from {@code index} on, rewritten. */
  private static Map<String, Object> rewrite(
      Map<String, Object> node, DocumentParameter parameter, int index) {
    if (index == parameter.path().size()) {
      return switch (parameter) {
        case DocumentParameter.Document ignored -> providerDocument(node);
        case DocumentParameter.TextOrDocument ignored -> providerTextOrDocument(node);
      };
    }
    final var step = parameter.path().get(index);
    return Collections.unmodifiableMap(
        Step.mapChildren(
            node,
            (childStep, child) ->
                childStep.equals(step) ? rewrite(child, parameter, index + 1) : child));
  }

  private static Map<String, Object> providerDocument(Map<String, Object> node) {
    final var result = new LinkedHashMap<>(node);
    putHint(result, HINT);
    return Collections.unmodifiableMap(withoutFormat(result));
  }

  private static Map<String, Object> withoutFormat(Map<String, Object> node) {
    node.remove(PROPERTY_FORMAT);
    node.put(PROPERTY_TYPE, TYPE_STRING);
    return node;
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

  @SuppressWarnings("unchecked")
  private static Map<String, Object> providerTextOrDocument(Map<String, Object> node) {
    final var schema = new LinkedHashMap<>(node);
    putHint(schema, UNION_HINT);
    final var keyword = Objects.requireNonNull(DocumentParameter.alternativesKeyword(schema));
    final var alternatives = new ArrayList<Object>();
    for (final var alternative : (List<Object>) Objects.requireNonNull(schema.get(keyword))) {
      final var provider =
          alternative instanceof Map<?, ?> map && DocumentParameter.isDocumentSchema(map)
              ? withoutFormat(new LinkedHashMap<>((Map<String, Object>) map))
              : alternative;
      if (!alternatives.contains(provider)) {
        alternatives.add(provider);
      }
    }

    final var result = new LinkedHashMap<String, Object>();
    if (alternatives.size() == 1 && alternatives.getFirst() instanceof Map<?, ?> only) {
      schema.forEach((key, value) -> {
        if (keyword.equals(key)) {
          only.forEach((onlyKey, onlyValue) -> {
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
