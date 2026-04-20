/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.document.Document;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts {@link Document} instances from tool call result content trees. Documents can appear at
 * any level: as the root content, within maps, within lists, or as properties of arbitrary objects.
 * Uses Jackson's bean introspection to walk object properties generically.
 */
public class ToolCallResultDocumentExtractor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ToolCallResultDocumentExtractor.class);

  private final ObjectMapper objectMapper;

  /** Documents extracted from a single tool call result, grouped with the tool call identity. */
  public record ToolCallDocuments(
      String toolCallId, String toolCallName, List<Document> documents) {}

  public ToolCallResultDocumentExtractor(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Extracts all {@link Document} instances from the given tool call results, grouped by tool call.
   * Tool calls without documents are excluded from the result. Order is preserved.
   */
  public List<ToolCallDocuments> extractDocuments(List<ToolCallResult> toolCallResults) {
    final var result = new ArrayList<ToolCallDocuments>();

    for (ToolCallResult toolCallResult : toolCallResults) {
      final var documents = extractDocuments(toolCallResult.content());
      if (!documents.isEmpty()) {
        result.add(
            new ToolCallDocuments(
                StringUtils.defaultString(toolCallResult.id()),
                StringUtils.defaultIfBlank(toolCallResult.name(), "unknown"),
                documents));
      }
    }

    return result;
  }

  /**
   * Recursively extracts all {@link Document} instances from an arbitrary object tree. Handles
   * {@link Document}, {@link Map}, {@link List}/{@link Collection}, and skips all other types.
   */
  public List<Document> extractDocuments(Object content) {
    if (content == null) {
      return List.of();
    }

    final var documents = new ArrayList<Document>();
    collectDocuments(content, documents);
    return documents;
  }

  private void collectDocuments(Object node, List<Document> documents) {
    if (node == null) {
      return;
    }

    switch (node) {
      case Document document -> documents.add(document);
      case Map<?, ?> map -> map.values().forEach(value -> collectDocuments(value, documents));
      case Collection<?> collection ->
          collection.forEach(element -> collectDocuments(element, documents));
      default -> collectDocumentsFromBeanProperties(node, documents);
    }
  }

  private void collectDocumentsFromBeanProperties(Object node, List<Document> documents) {
    if (isScalar(node)) {
      return;
    }

    try {
      var type = objectMapper.constructType(node.getClass());
      BeanDescription desc = objectMapper.getSerializationConfig().introspect(type);
      List<BeanPropertyDefinition> properties = desc.findProperties();

      for (BeanPropertyDefinition property : properties) {
        var accessor = property.getAccessor();
        if (accessor != null) {
          accessor.fixAccess(true);
          Object value = accessor.getValue(node);
          collectDocuments(value, documents);
        }
      }
    } catch (Exception e) {
      LOGGER.debug(
          "Failed to introspect bean properties of {}: {}", node.getClass(), e.getMessage());
    }
  }

  private static boolean isScalar(Object node) {
    return node instanceof String
        || node instanceof Number
        || node instanceof Boolean
        || node instanceof Enum<?>;
  }
}
