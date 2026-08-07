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
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.AssistantMessageBuilder;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
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

  private static final String FRAMEWORK_METADATA_KEY = "framework";

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
      AssistantMessage assistantMessage, ProviderConfiguration providerConfiguration) {
    return fromAssistantMessageBuilder(assistantMessage, providerConfiguration).build();
  }

  protected dev.langchain4j.data.message.AiMessage.Builder fromAssistantMessageBuilder(
      AssistantMessage assistantMessage, ProviderConfiguration providerConfiguration) {
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

    final var attributes = toolCallAttributes(assistantMessage, providerConfiguration);
    if (!attributes.isEmpty()) {
      builder.attributes(attributes);
    }

    return builder;
  }

  /**
   * Provider tool call metadata carries data the provider requires us to echo back verbatim on the
   * next request, e.g. Gemini 3 thought signatures. This is provider-specific data, kept separate
   * from {@link #serializedChatResponseMetadata}. What is safe to restore is provider-specific -
   * see {@link ToolCallMetadataDecorator}.
   */
  protected Map<String, Object> toolCallAttributes(
      AssistantMessage assistantMessage, ProviderConfiguration providerConfiguration) {
    if (CollectionUtils.isEmpty(assistantMessage.toolCalls())) {
      return Map.of();
    }

    final var attributes = new LinkedHashMap<String, Object>();
    for (final var toolCall : assistantMessage.toolCalls()) {
      if (CollectionUtils.isEmpty(toolCall.metadata())) {
        continue;
      }

      attributes.putAll(
          ToolCallMetadataDecorator.decorateOnRead(
              providerConfiguration, toolCall.id(), toolCall.metadata()));
    }

    return attributes;
  }

  @Override
  public AssistantMessage toAssistantMessage(
      ChatResponse chatResponse, ProviderConfiguration providerConfiguration) {
    return toAssistantMessageBuilder(chatResponse, providerConfiguration).build();
  }

  protected AssistantMessageBuilder toAssistantMessageBuilder(
      ChatResponse chatResponse, ProviderConfiguration providerConfiguration) {
    final var builder = AssistantMessage.builder();
    final var aiMessage = chatResponse.aiMessage();

    if (chatResponse.metadata() != null) {
      final var metadata = new LinkedHashMap<String, Object>();
      metadata.put("timestamp", ZonedDateTime.now());
      metadata.put(FRAMEWORK_METADATA_KEY, serializedChatResponseMetadata(chatResponse.metadata()));

      builder.metadata(metadata);
    }

    if (StringUtils.isNotBlank(aiMessage.text())) {
      builder.content(List.of(TextContent.textContent(aiMessage.text())));
    }

    final var toolCalls =
        aiMessage.toolExecutionRequests().stream()
            .map(toolCallConverter::asToolCall)
            .map(toolCall -> decorateToolCallMetadata(toolCall, aiMessage, providerConfiguration))
            .toList();

    builder.toolCalls(toolCalls);

    return builder;
  }

  private ToolCall decorateToolCallMetadata(
      ToolCall toolCall, AiMessage aiMessage, ProviderConfiguration providerConfiguration) {
    if (CollectionUtils.isEmpty(aiMessage.attributes())) {
      return toolCall;
    }

    final var metadata =
        ToolCallMetadataDecorator.decorateOnWrite(
            providerConfiguration, toolCall.id(), aiMessage.attributes());

    return metadata.isEmpty() ? toolCall : toolCall.withMetadata(metadata);
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
