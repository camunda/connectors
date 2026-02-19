/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import static io.camunda.connector.agenticai.util.JacksonExceptionMessageExtractor.humanReadableJsonProcessingExceptionMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolCallConverter;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.AssistantMessageBuilder;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.util.ObjectMapperConstants;
import io.camunda.connector.api.error.ConnectorException;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

public class ChatMessageConverterImpl implements ChatMessageConverter {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChatMessageConverterImpl.class);

  private final ContentConverter contentConverter;
  private final ToolCallConverter toolCallConverter;
  private final ObjectMapper objectMapper;

  public ChatMessageConverterImpl(
      ContentConverter contentConverter,
      ToolCallConverter toolCallConverter,
      ObjectMapper objectMapper) {
    this.contentConverter = contentConverter;
    this.toolCallConverter = toolCallConverter;
    this.objectMapper = objectMapper;
  }

  @Override
  public dev.langchain4j.data.message.SystemMessage fromSystemMessage(SystemMessage systemMessage) {
    if (systemMessage.content().size() == 1
        && systemMessage.content().getFirst() instanceof TextContent textContent) {
      return new dev.langchain4j.data.message.SystemMessage(textContent.text());
    }

    throw new IllegalArgumentException(
        "SystemMessage currently only supports a single TextContent block.");
  }

  @Override
  public dev.langchain4j.data.message.UserMessage fromUserMessage(UserMessage userMessage) {
    return userMessageBuilder(userMessage).build();
  }

  protected dev.langchain4j.data.message.UserMessage.Builder userMessageBuilder(
      UserMessage userMessage) {
    if (CollectionUtils.isEmpty(userMessage.content())) {
      throw new IllegalArgumentException("UserMessage content cannot be empty");
    }

    final var builder = dev.langchain4j.data.message.UserMessage.builder();
    Optional.ofNullable(userMessage.name()).ifPresent(builder::name);

    for (Content content : userMessage.content()) {
      try {
        builder.addContent(contentConverter.convertToContent(content));
      } catch (JsonProcessingException e) {
        throw new ConnectorException(
            "Failed to convert user message content to string: %s"
                .formatted(humanReadableJsonProcessingExceptionMessage(e)));
      }
    }

    return builder;
  }

  @Override
  public dev.langchain4j.data.message.AiMessage fromAssistantMessage(
      AssistantMessage assistantMessage) {
    return fromAssistantMessageBuilder(assistantMessage).build();
  }

  protected dev.langchain4j.data.message.AiMessage.Builder fromAssistantMessageBuilder(
      AssistantMessage assistantMessage) {
    final var builder = AiMessage.builder();

    if (!CollectionUtils.isEmpty(assistantMessage.content())) {
      if (assistantMessage.content().size() != 1
          || !(assistantMessage.content().getFirst() instanceof TextContent textContent)) {
        throw new IllegalArgumentException(
            "AiMessage currently only supports a single TextContent block, %d content blocks found instead."
                .formatted(assistantMessage.content().size()));
      }

      builder.text(textContent.text());
    }

    final var toolExecutionRequests =
        assistantMessage.toolCalls().stream()
            .map(toolCallConverter::asToolExecutionRequest)
            .toList();

    if (!toolExecutionRequests.isEmpty()) {
      builder.toolExecutionRequests(toolExecutionRequests);
    }

    return builder;
  }

  @Override
  public AssistantMessage toAssistantMessage(ChatResponse chatResponse) {
    return toAssistantMessageBuilder(chatResponse).build();
  }

  protected AssistantMessageBuilder toAssistantMessageBuilder(ChatResponse chatResponse) {
    final var builder = AssistantMessage.builder();

    if (chatResponse.metadata() != null) {
      builder.metadata(
          Map.of(
              "timestamp", ZonedDateTime.now(),
              "framework", serializedChatResponseMetadata(chatResponse.metadata())));
    }

    final var aiMessage = chatResponse.aiMessage();
    if (StringUtils.isNotBlank(aiMessage.text())) {
      builder.content(List.of(TextContent.textContent(aiMessage.text())));
    }

    final var toolCalls =
        aiMessage.toolExecutionRequests().stream().map(toolCallConverter::asToolCall).toList();

    builder.toolCalls(toolCalls);

    return builder;
  }

  protected Map<String, Object> serializedChatResponseMetadata(
      ChatResponseMetadata chatResponseMetadata) {
    if (chatResponseMetadata == null) {
      return Map.of();
    }

    final var metadata = new LinkedHashMap<String, Object>();
    Optional.ofNullable(chatResponseMetadata.id())
        .filter(StringUtils::isNotBlank)
        .ifPresent(id -> metadata.put("id", id));
    Optional.ofNullable(chatResponseMetadata.finishReason())
        .ifPresent(finishReason -> metadata.put("finishReason", finishReason.name()));

    final var tokenUsage = serializedTokenUsage(chatResponseMetadata.tokenUsage());
    if (!tokenUsage.isEmpty()) {
      metadata.put("tokenUsage", tokenUsage);
    }

    return metadata;
  }

  protected Map<String, Object> serializedTokenUsage(TokenUsage tokenUsage) {
    if (tokenUsage == null) {
      return Map.of();
    }

    try {
      return objectMapper.readValue(
          Json.toJson(tokenUsage), ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE);
    } catch (JsonProcessingException e) {
      LOGGER.warn(
          "Failed to deserialize token usage metadata: {}",
          humanReadableJsonProcessingExceptionMessage(e));
      return Map.of();
    }
  }

  @Override
  public List<ToolExecutionResultMessage> fromToolCallResultMessage(
      ToolCallResultMessage toolCallResultMessage) {
    return toolCallResultMessage.results().stream()
        .map(toolCallConverter::asToolExecutionResultMessage)
        .toList();
  }
}
