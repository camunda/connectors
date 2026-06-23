/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool;

import static dev.langchain4j.data.message.ToolExecutionResultMessage.toolExecutionResultMessage;
import static io.camunda.connector.agenticai.util.JacksonExceptionMessageExtractor.humanReadableJsonProcessingExceptionMessage;
import static io.camunda.connector.agenticai.util.ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.camunda.connector.agenticai.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;

public class ToolCallConverterImpl implements ToolCallConverter {

  private final ObjectMapper objectMapper;

  public ToolCallConverterImpl(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public ToolExecutionRequest asToolExecutionRequest(ToolCall toolCall) {
    try {
      return ToolExecutionRequest.builder()
          .id(toolCall.id())
          .name(toolCall.name())
          .arguments(objectMapper.writeValueAsString(toolCall.arguments()))
          .build();
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          "Failed to serialize tool call results for tool '%s': %s"
              .formatted(toolCall.name(), humanReadableJsonProcessingExceptionMessage(e)));
    }
  }

  @Override
  public ToolCall asToolCall(ToolExecutionRequest toolExecutionRequest) {
    return asToolCall(
        Optional.ofNullable(toolExecutionRequest.id())
            .filter(StringUtils::isNotBlank)
            .orElse(UUID.randomUUID().toString()),
        toolExecutionRequest.name(),
        toolExecutionRequest.arguments());
  }

  private ToolCall asToolCall(String id, String name, String inputJson) {
    try {
      Map<String, Object> arguments =
          objectMapper.readValue(inputJson, STRING_OBJECT_MAP_TYPE_REFERENCE);
      return new ToolCall(id, name, arguments);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          "Failed to deserialize tool call results for tool '%s': %s"
              .formatted(name, humanReadableJsonProcessingExceptionMessage(e)));
    } catch (Exception e) {
      throw new ConnectorException(
          "Failed to deserialize tool call results for tool '%s': %s"
              .formatted(name, e.getMessage()));
    }
  }

  /**
   * Converts the result of a tool call to a {@link ToolExecutionResultMessage}.
   *
   * <p>If the result is not a string, it will be serialized to a JSON string using the connectors
   * ObjectMapper. {@link Document} instances in the content tree are rendered as {@code <doc id="…"
   * fileName="…" contentType="…"/>} tags (without tool attribution — the tool attribution is
   * emitted at Site 2, in the separate content-bearing user message). All other values are
   * serialized to JSON.
   */
  @Override
  public ToolExecutionResultMessage asToolExecutionResultMessage(ToolCallResult toolCallResult) {
    final var id = Objects.requireNonNullElse(toolCallResult.id(), "");
    final var name = Objects.requireNonNullElse(toolCallResult.name(), "");

    var content = contentAsString(name, toolCallResult.content());
    if (StringUtils.isBlank(content)) {
      content = ToolCallResult.CONTENT_NO_RESULT;
    }

    return toolExecutionResultMessage(id, name, content);
  }

  /**
   * Converts a content tree value to a string for the LLM tool result.
   *
   * <p>{@link Document} leaf nodes are replaced with {@code <doc/>} XML tags (no tool attribution —
   * see Site 1 vs Site 2 in §11.4 of the skills design doc). Other values are serialized via JSON
   * (which will encode any remaining document references as their JSON reference shapes, e.g. for
   * document references not wrapped in a {@link Document}).
   */
  private String contentAsString(String toolName, Object result) {
    try {
      if (result == null) {
        return null;
      }
      if (result instanceof String s) {
        return s;
      }
      // Substitute Document leaf nodes in the content tree with <doc/> XML tags before
      // JSON-serializing the rest. This produces stable, model-safe ids (from DocumentHandle)
      // instead of the raw reference JSON that the ObjectMapper would emit for Document objects.
      final var substituted = substituteDocuments(result);
      return objectMapper.writeValueAsString(substituted);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          "Failed to convert result of tool call '%s' to string: %s"
              .formatted(toolName, humanReadableJsonProcessingExceptionMessage(e)));
    }
  }

  /**
   * Recursively walks the content tree and replaces {@link Document} leaf nodes with their {@code
   * <doc/>} XML tag strings. Maps, Lists, and arrays are traversed; all other non-Document types
   * are returned unchanged.
   */
  private static Object substituteDocuments(Object node) {
    return switch (node) {
      case null -> null;
      case Document doc -> DocumentReferenceXmlTag.from(doc).toXml();
      case Map<?, ?> map -> {
        final var result = new LinkedHashMap<Object, Object>(map.size());
        map.forEach((k, v) -> result.put(k, substituteDocuments(v)));
        yield result;
      }
      case List<?> list -> {
        final var result = new ArrayList<>(list.size());
        list.forEach(item -> result.add(substituteDocuments(item)));
        yield result;
      }
      case Collection<?> collection -> {
        final var result = new ArrayList<>();
        collection.forEach(item -> result.add(substituteDocuments(item)));
        yield result;
      }
      case Object[] array -> {
        final var result = new Object[array.length];
        for (int i = 0; i < array.length; i++) {
          result[i] = substituteDocuments(array[i]);
        }
        yield result;
      }
      default -> node;
    };
  }
}
