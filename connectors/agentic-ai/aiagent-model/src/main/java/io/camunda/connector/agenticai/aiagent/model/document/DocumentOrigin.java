/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.model.message.MessageId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Where a document entered the conversation, as persisted in the agent documents. It is serialized
 * with the discriminator {@code type}: {@code user_message}, {@code event} or {@code tool_call}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DocumentOrigin.UserMessage.class, name = "user_message"),
  @JsonSubTypes.Type(value = DocumentOrigin.Event.class, name = "event"),
  @JsonSubTypes.Type(value = DocumentOrigin.ToolCall.class, name = "tool_call")
})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface DocumentOrigin {

  /**
   * The document is part of the user prompt.
   *
   * @param messageId the id of the user message that holds the document
   */
  record UserMessage(MessageId messageId) implements DocumentOrigin {
    public UserMessage {
      Objects.requireNonNull(messageId, "messageId must not be null");
    }
  }

  /**
   * The document is part of event data.
   *
   * @param messageId the id of the user message of the event that holds the document
   */
  record Event(MessageId messageId) implements DocumentOrigin {
    public Event {
      Objects.requireNonNull(messageId, "messageId must not be null");
    }
  }

  /**
   * The document was returned by a tool call.
   *
   * @param toolName the name of the tool that returned the document
   * @param toolCallId the id of the tool call that returned the document
   */
  record ToolCall(@Nullable String toolName, @Nullable String toolCallId)
      implements DocumentOrigin {}

  static DocumentOrigin userMessage(MessageId messageId) {
    return new UserMessage(messageId);
  }

  static DocumentOrigin event(MessageId messageId) {
    return new Event(messageId);
  }

  static DocumentOrigin toolCall(@Nullable String toolName, @Nullable String toolCallId) {
    return new ToolCall(toolName, toolCallId);
  }
}
