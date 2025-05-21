/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
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
import io.camunda.document.Document;
import java.util.List;
import java.util.Optional;
import org.springframework.util.CollectionUtils;

public class ChatMessageConverterImpl implements ChatMessageConverter {

  private final ToolCallConverter toolCallConverter;
  private final DocumentToContentConverter documentToContentConverter;

  public ChatMessageConverterImpl(
      ToolCallConverter toolCallConverter, DocumentToContentConverter documentToContentConverter) {
    this.toolCallConverter = toolCallConverter;
    this.documentToContentConverter = documentToContentConverter;
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

    final var textContent =
        assistantMessage.content().stream().filter(c -> c instanceof TextContent).toList();

    if (textContent.size() > 1) {
      throw new IllegalArgumentException(
          "AiMessage currently only supports a single TextContent block, %d found"
              .formatted(textContent.size()));
    } else if (textContent.size() == 1) {
      builder.text(((TextContent) textContent.getFirst()).text());
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
  public AssistantMessage toAssistantMessage(AiMessage aiMessage) {
    return toAssistantMessageBuilder(aiMessage).build();
  }

  protected AssistantMessageBuilder toAssistantMessageBuilder(AiMessage aiMessage) {
    final var builder = AssistantMessage.builder();
    builder.content(List.of(TextContent.textContent(aiMessage.text())));

    final var toolCalls =
        aiMessage.toolExecutionRequests().stream().map(toolCallConverter::asToolCall).toList();

    builder.toolCalls(toolCalls);

    return builder;
  }

  @Override
  public List<ToolExecutionResultMessage> fromToolCallResultMessage(
      ToolCallResultMessage toolCallResultMessage) {
    return toolCallResultMessage.results().stream()
        .map(toolCallConverter::asToolExecutionResultMessage)
        .toList();
  }
}
