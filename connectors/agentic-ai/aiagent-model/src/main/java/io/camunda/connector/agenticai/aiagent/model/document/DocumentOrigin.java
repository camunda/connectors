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

  /** The document is part of the user prompt. */
  record UserMessage() implements DocumentOrigin {}

  /** The document is part of event data. */
  record Event() implements DocumentOrigin {}

  /**
   * The document was returned by a tool call.
   *
   * @param toolName the name of the tool that returned the document
   * @param toolCallId the id of the tool call that returned the document
   */
  record ToolCall(@Nullable String toolName, @Nullable String toolCallId)
      implements DocumentOrigin {}

  static DocumentOrigin userMessage() {
    return new UserMessage();
  }

  static DocumentOrigin event() {
    return new Event();
  }

  static DocumentOrigin toolCall(@Nullable String toolName, @Nullable String toolCallId) {
    return new ToolCall(toolName, toolCallId);
  }
}
