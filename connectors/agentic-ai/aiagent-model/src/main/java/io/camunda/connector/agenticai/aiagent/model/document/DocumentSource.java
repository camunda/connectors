/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/**
 * Where a document entered the conversation, as persisted in the document registry.
 *
 * @param type the kind of input the document came with
 * @param toolName the name of the tool that returned the document, only for {@link Type#TOOL_CALL}
 * @param toolCallId the id of the tool call that returned the document, only for {@link
 *     Type#TOOL_CALL}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentSource(Type type, @Nullable String toolName, @Nullable String toolCallId) {

  /** The kind of input a document came with. */
  public enum Type {
    /** The document was returned by a tool call. */
    TOOL_CALL(null),
    /** The document is part of the user prompt. */
    USER_MESSAGE("user_message"),
    /** The document is part of event data. */
    EVENT("event");

    private final @Nullable String tagValue;

    Type(@Nullable String tagValue) {
      this.tagValue = tagValue;
    }

    /**
     * The value of the {@code source} attribute of the document tag; {@code null} for a tool call
     * document, whose tag carries {@code toolName} and {@code toolCallId} instead.
     */
    public @Nullable String tagValue() {
      return tagValue;
    }
  }

  public static DocumentSource toolCall(@Nullable String toolName, @Nullable String toolCallId) {
    return new DocumentSource(Type.TOOL_CALL, toolName, toolCallId);
  }

  public static DocumentSource userMessage() {
    return new DocumentSource(Type.USER_MESSAGE, null, null);
  }

  public static DocumentSource event() {
    return new DocumentSource(Type.EVENT, null, null);
  }
}
