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
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

public class ChatMessageConverterImpl implements ChatMessageConverter {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChatMessageConverterImpl.class);

  private static final String FRAMEWORK_METADATA_KEY = "framework";
  private static final String ATTRIBUTES_METADATA_KEY = "attributes";

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

    final var attributes = frameworkAttributes(assistantMessage);
    if (!attributes.isEmpty()) {
      builder.attributes(attributes);
    }

    return builder;
  }

  /**
   * Framework attributes carry data the provider requires us to echo back verbatim on the next
   * request. Gemini 3 rejects a request whose function calls are missing their {@code
   * thoughtSignature}, which langchain4j round-trips through {@link AiMessage#attributes()} keyed
   * by tool call ID, so they have to survive being persisted to the conversation history.
   */
  protected Map<String, Object> frameworkAttributes(AssistantMessage assistantMessage) {
    if (CollectionUtils.isEmpty(assistantMessage.metadata())) {
      return Map.of();
    }

    // messages persisted before attributes were supported have no "attributes" key - a missing key
    // yields null, which fails the instanceof and short-circuits to an empty map
    if (!(assistantMessage.metadata().get(FRAMEWORK_METADATA_KEY) instanceof Map<?, ?> framework)
        || !(framework.get(ATTRIBUTES_METADATA_KEY) instanceof Map<?, ?> attributes)) {
      return Map.of();
    }

    // the conversation is stored in a process variable, so the contents are neither type-safe nor
    // beyond tampering. Keep only the string values langchain4j writes - anything else would fail
    // as an unhelpful ClassCastException inside the provider's mapper.
    return attributes.entrySet().stream()
        .filter(entry -> entry.getKey() instanceof String && entry.getValue() instanceof String)
        .collect(Collectors.toMap(entry -> (String) entry.getKey(), Map.Entry::getValue));
  }

  @Override
  public AssistantMessage toAssistantMessage(ChatResponse chatResponse) {
    return toAssistantMessageBuilder(chatResponse).build();
  }

  protected AssistantMessageBuilder toAssistantMessageBuilder(ChatResponse chatResponse) {
    final var builder = AssistantMessage.builder();
    final var aiMessage = chatResponse.aiMessage();

    if (chatResponse.metadata() != null || !CollectionUtils.isEmpty(aiMessage.attributes())) {
      final var frameworkMetadata =
          new LinkedHashMap<>(serializedChatResponseMetadata(chatResponse.metadata()));

      // see frameworkAttributes(...) - these must be echoed back on the next request
      if (!CollectionUtils.isEmpty(aiMessage.attributes())) {
        frameworkMetadata.put(ATTRIBUTES_METADATA_KEY, aiMessage.attributes());
      }

      builder.metadata(
          Map.of("timestamp", ZonedDateTime.now(), FRAMEWORK_METADATA_KEY, frameworkMetadata));
    }

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
