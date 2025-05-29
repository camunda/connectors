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
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolCallConverter;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.AssistantMessageBuilder;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.util.ObjectMapperConstants;
import io.camunda.document.Document;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.util.CollectionUtils;

public class ChatMessageConverterImpl implements ChatMessageConverter {

  private final ToolCallConverter toolCallConverter;
  private final DocumentToContentConverter documentToContentConverter;
  private final ObjectMapper objectMapper;

  public ChatMessageConverterImpl(
      ToolCallConverter toolCallConverter,
      DocumentToContentConverter documentToContentConverter,
      ObjectMapper objectMapper) {
    this.toolCallConverter = toolCallConverter;
    this.documentToContentConverter = documentToContentConverter;
    this.objectMapper = objectMapper;
  }

  @Override
  public dev.langchain4j.data.message.SystemMessage fromSystemMessage(SystemMessage systemMessage) {
    if (systemMessage.content().size() == 1
        && systemMessage.content().getFirst() instanceof TextContent(String text)) {
      return new dev.langchain4j.data.message.SystemMessage(text);
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
      switch (content) {
        case TextContent(String text) ->
            builder.addContent(new dev.langchain4j.data.message.TextContent(text));
        case DocumentContent(Document document) ->
            builder.addContent(documentToContentConverter.convert(document));
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

    if (assistantMessage.content().size() != 1
        || !(assistantMessage.content().getFirst() instanceof TextContent(String text))) {
      throw new IllegalArgumentException(
          "AiMessage currently only supports a single TextContent block, %d content blocks found instead."
              .formatted(assistantMessage.content().size()));
    }

    builder.text(text);

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
          Map.of("framework", serializedChatResponseMetadata(chatResponse.metadata())));
    }

    final var aiMessage = chatResponse.aiMessage();
    builder.content(List.of(TextContent.textContent(aiMessage.text())));

    final var toolCalls =
        aiMessage.toolExecutionRequests().stream().map(toolCallConverter::asToolCall).toList();

    builder.toolCalls(toolCalls);

    return builder;
  }

  protected Map<String, Object> serializedChatResponseMetadata(ChatResponseMetadata metadata) {
    if (metadata == null) {
      return Map.of();
    }

    try {
      return objectMapper.readValue(
          Json.toJson(metadata), ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(
          "Failed to deserialize chat response metadata: %s"
              .formatted(humanReadableJsonProcessingExceptionMessage(e)));
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
