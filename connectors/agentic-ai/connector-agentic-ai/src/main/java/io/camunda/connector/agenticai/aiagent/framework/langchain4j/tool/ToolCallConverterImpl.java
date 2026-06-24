/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool;

import static dev.langchain4j.data.message.ToolExecutionResultMessage.toolExecutionResultMessage;
import static io.camunda.connector.agenticai.aiagent.util.JacksonExceptionMessageExtractor.humanReadableJsonProcessingExceptionMessage;
import static io.camunda.connector.agenticai.common.util.ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentReferenceTagSerializer;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

public class ToolCallConverterImpl implements ToolCallConverter {

  private final ObjectMapper objectMapper;
  private final ObjectMapper documentTagObjectMapper;

  public ToolCallConverterImpl(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.documentTagObjectMapper =
        objectMapper
            .copy()
            .registerModule(
                new SimpleModule()
                    .addSerializer(Document.class, new DocumentReferenceTagSerializer()));
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
   * <p>{@link Document} nodes at any nesting depth in the content tree are rendered as {@code
   * <doc/>} XML tags via the registered {@link DocumentReferenceTagSerializer} (no tool attribution
   * — Site 1). All other values are serialized to JSON.
   */
  private @Nullable String contentAsString(String toolName, @Nullable Object result) {
    try {
      if (result == null) {
        return null;
      }
      if (result instanceof String s) {
        return s;
      }
      return documentTagObjectMapper.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          "Failed to convert result of tool call '%s' to string: %s"
              .formatted(toolName, humanReadableJsonProcessingExceptionMessage(e)));
    }
  }
}
