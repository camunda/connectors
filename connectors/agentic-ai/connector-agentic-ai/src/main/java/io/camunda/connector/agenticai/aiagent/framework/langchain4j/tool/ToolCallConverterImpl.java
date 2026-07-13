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
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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
   * Converts the structured result of a tool call to a {@link ToolExecutionResultMessage}.
   *
   * <p>Each {@link Content} element of the result is rendered to a string ({@link TextContent}
   * verbatim, {@link ObjectContent}/{@link DocumentContent} via JSON serialization using the
   * connectors ObjectMapper — document instances are serialized as document references) and the
   * per-element strings are joined with {@code "\n"}. Since C4 (structured tool-call-result
   * storage) always produces a singleton or empty content list, this yields byte-identical output
   * to the pre-C4 stringification of a raw {@code ToolCallResult.content()} for every real case.
   */
  @Override
  public ToolExecutionResultMessage asToolExecutionResultMessage(
      ToolCallResultContent toolCallResult) {
    final var id = Objects.requireNonNullElse(toolCallResult.id(), "");
    final var name = Objects.requireNonNullElse(toolCallResult.name(), "");

    var content = contentAsString(name, toolCallResult.content());
    if (StringUtils.isBlank(content)) {
      content = ToolCallResult.CONTENT_NO_RESULT;
    }

    return toolExecutionResultMessage(id, name, content);
  }

  private String contentAsString(String toolName, List<Content> content) {
    return content.stream()
        .map(element -> contentElementAsString(toolName, element))
        .collect(Collectors.joining("\n"));
  }

  private String contentElementAsString(String toolName, Content content) {
    try {
      return switch (content) {
        case TextContent textContent -> textContent.text();
        case ObjectContent objectContent ->
            objectMapper.writeValueAsString(objectContent.content());
        case DocumentContent documentContent ->
            objectMapper.writeValueAsString(documentContent.document());
        case ReasoningContent reasoningContent ->
            throw new IllegalArgumentException(
                "Reasoning content is not expected in tool call results");
        case ProviderContent providerContent ->
            throw new IllegalArgumentException(
                "Provider content is not expected in tool call results");
      };
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          "Failed to convert result of tool call '%s' to string: %s"
              .formatted(toolName, humanReadableJsonProcessingExceptionMessage(e)));
    }
  }
}
