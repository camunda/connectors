/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j;

import dev.langchain4j.model.chat.ChatModel;
import java.util.Map;

/**
 * A {@link ChatModel} that owns a closeable resource (e.g. an HTTP connection pool) and must be
 * closed after use. Declares {@code close()} without a checked exception to align with AWS SDK's
 * {@code SdkAutoCloseable}.
 */
public interface CloseableChatModel extends ChatModel, AutoCloseable {
  @Override
  void close();

  /**
   * Extracts the subset of {@code AiMessage.attributes()} that is safe to persist into a Camunda
   * process variable as this tool call's metadata.
   *
   * <p>{@code AiMessage.attributes()} is a generic map, populated only by langchain4j's own
   * provider-specific integration code, and is documented as "typically provider-specific". Since
   * the persisted {@code ToolCall} lives in an untyped process variable, it must never be dumped
   * verbatim - every chat model drops attributes entirely unless it overrides this method.
   *
   * <p>Called with the full, flat {@code aiMessage.attributes()} (never null, may be empty) and the
   * ID of the specific tool call being converted, when a response is first turned into a persisted
   * {@link io.camunda.connector.agenticai.aiagent.model.tool.ToolCall}.
   *
   * @return the metadata to persist on that tool call.
   */
  default Map<String, Object> extractToolCallMetadata(
      String toolCallId, Map<String, Object> aiMessageAttributes) {
    return Map.of();
  }

  /**
   * The inverse of {@link #extractToolCallMetadata}: called with the already-unwrapped {@code
   * toolCall.metadata()} (never null, may be empty, untyped since it round-tripped through a
   * process variable) of a single tool call, when a persisted tool call is turned back into a
   * langchain4j request for the next model call. Returns the entry to restore onto the outgoing
   * {@code AiMessage.attributes()}, keyed the way langchain4j expects.
   */
  default Map<String, Object> restoreToolCallAttributes(
      String toolCallId, Map<?, ?> toolCallMetadata) {
    return Map.of();
  }
}
