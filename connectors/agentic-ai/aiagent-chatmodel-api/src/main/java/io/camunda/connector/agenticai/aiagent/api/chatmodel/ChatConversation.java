/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.chatmodel;

import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ChatContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.PortableContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;
import java.util.Optional;

/**
 * The conversation of one chat request as a provider receives it: the messages and the tool
 * definitions. Its types hold no content block that only the agent uses, such as an {@link
 * AgentDocumentContent}.
 */
public record ChatConversation(
    List<ChatRequestMessage> messages, List<ToolDefinition> toolDefinitions) {

  public ChatConversation {
    messages = List.copyOf(messages);
    toolDefinitions = List.copyOf(toolDefinitions);
  }

  /**
   * The conversation of the given persisted messages, in the same order, with the same content
   * blocks.
   *
   * @throws IllegalArgumentException if a message is not a {@link SystemMessage}, {@link
   *     UserMessage}, {@link AssistantMessage} or {@link ToolCallResultMessage}, or if a message
   *     holds a block that a request message of its role cannot hold: an {@link
   *     AgentDocumentContent} in any message, a {@link ReasoningContent} or a {@link
   *     ProviderContent} outside an assistant message, or a block other than {@link TextContent} in
   *     a system message
   */
  public static ChatConversation of(List<Message> messages, List<ToolDefinition> toolDefinitions) {
    return new ChatConversation(
        messages.stream().map(ChatConversation::requestMessage).toList(), toolDefinitions);
  }

  /** The first message, when it is a system message. */
  public Optional<ChatSystemMessage> leadingSystemMessage() {
    return !messages.isEmpty() && messages.getFirst() instanceof ChatSystemMessage systemMessage
        ? Optional.of(systemMessage)
        : Optional.empty();
  }

  private static ChatRequestMessage requestMessage(Message message) {
    return switch (message) {
      case SystemMessage system -> new ChatSystemMessage(textContent(system.content()));
      case UserMessage user ->
          new ChatUserMessage(portableContent(user.content(), "A user message"));
      case AssistantMessage assistant ->
          new ChatAssistantMessage(
              chatContent(assistant.content()),
              assistant.toolCalls(),
              assistant.messageId(),
              assistant.metadata());
      case ToolCallResultMessage resultMessage ->
          new ChatToolCallResultMessage(
              resultMessage.results().stream().map(ChatConversation::toolCallResult).toList());
      default ->
          throw new IllegalArgumentException(
              "Unsupported message type '%s' in a chat request"
                  .formatted(message.getClass().getSimpleName()));
    };
  }

  private static ChatToolCallResult toolCallResult(ToolCallResultContent result) {
    return new ChatToolCallResult(
        result.id(), result.name(), portableContent(result.content(), "A tool call result"));
  }

  private static List<TextContent> textContent(List<Content> content) {
    return content.stream()
        .map(block ->
            switch (block) {
              case TextContent text -> text;
              case DocumentContent ignored -> throw unsupported("A system message", block);
              case ObjectContent ignored -> throw unsupported("A system message", block);
              case ReasoningContent ignored -> throw unsupported("A system message", block);
              case ProviderContent ignored -> throw unsupported("A system message", block);
              case AgentDocumentContent ignored -> throw unsupported("A system message", block);
            })
        .toList();
  }

  private static List<PortableContent> portableContent(List<Content> content, String holder) {
    return content.stream()
        .map(block ->
            switch (block) {
              case PortableContent portable -> portable;
              case ReasoningContent ignored -> throw unsupported(holder, block);
              case ProviderContent ignored -> throw unsupported(holder, block);
              case AgentDocumentContent ignored -> throw unsupported(holder, block);
            })
        .toList();
  }

  private static List<ChatContent> chatContent(List<Content> content) {
    return content.stream()
        .map(block ->
            switch (block) {
              case ChatContent chatContent -> chatContent;
              case AgentDocumentContent ignored -> throw unsupported("An assistant message", block);
            })
        .toList();
  }

  private static IllegalArgumentException unsupported(String holder, Content block) {
    return new IllegalArgumentException(
        "%s of a chat request cannot hold content of type '%s'"
            .formatted(holder, block.getClass().getSimpleName()));
  }
}
