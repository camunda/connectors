/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.chatmodel;

import io.camunda.connector.agenticai.aiagent.model.message.content.ChatContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * An assistant message of a chat request. It is the only message that can hold reasoning and
 * provider-native content.
 *
 * @param content the content blocks
 * @param toolCalls the tool calls of the message
 * @param messageId the id that the provider gave the message, if any
 * @param metadata the metadata of the message, empty when it has none
 */
public record ChatAssistantMessage(
    List<ChatContent> content,
    List<ToolCall> toolCalls,
    @Nullable String messageId,
    Map<String, Object> metadata)
    implements ChatRequestMessage {

  public ChatAssistantMessage(
      List<ChatContent> content,
      List<ToolCall> toolCalls,
      @Nullable String messageId,
      @Nullable Map<String, Object> metadata) {
    this.content = List.copyOf(content);
    this.toolCalls = List.copyOf(toolCalls);
    this.messageId = messageId;
    this.metadata =
        metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
  }
}
