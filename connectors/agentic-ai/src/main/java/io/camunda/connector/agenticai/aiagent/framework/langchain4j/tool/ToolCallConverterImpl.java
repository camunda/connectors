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
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ContentConverter;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;

public class ToolCallConverterImpl implements ToolCallConverter {

  private final ObjectMapper objectMapper;
  private final ContentConverter contentConverter;

  public ToolCallConverterImpl(ObjectMapper objectMapper, ContentConverter contentConverter) {
    this.objectMapper = objectMapper;
    this.contentConverter = contentConverter;
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
   * <p>If the result is not a string, it will be serialized to a JSON string, using the {@link
   * ContentConverter} to serialize document contents.
   */
  @Override
  public ToolExecutionResultMessage asToolExecutionResultMessage(ToolCallResult toolCallResult) {
    final var id = Objects.requireNonNullElse(toolCallResult.id(), "");
    final var name = Objects.requireNonNullElse(toolCallResult.name(), "");

    return toolExecutionResultMessage(id, name, contentAsString(name, toolCallResult.content()));
  }

  private String contentAsString(String toolName, Object result) {
    try {
      return contentConverter.convertToString(result);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          "Failed to convert result of tool call '%s' to string: %s"
              .formatted(toolName, humanReadableJsonProcessingExceptionMessage(e)));
    }
  }
}
