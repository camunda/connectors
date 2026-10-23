/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j;

import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import java.util.Map;

/**
 * Carries provider data that a provider requires to be echoed back verbatim on the next request,
 * such as the thought signatures of Gemini 3, through {@code ToolCall#metadata()}. LangChain4j
 * returns this data as attributes of the {@code AiMessage}, and expects it again as attributes of
 * the replayed {@code AiMessage}.
 *
 * <p>The bridge does not know any provider, so the default carries nothing. Register a bean of this
 * type to carry the data of a provider. The configuration tells which provider the call is for, so
 * one decorator can serve several providers.
 */
public interface ToolCallMetadataDecorator {

  /**
   * Called on the write path with the full, flat {@code aiMessage.attributes()} (never null, may be
   * empty) and the ID of the specific tool call being converted.
   *
   * @return the namespaced metadata to persist on that tool call.
   */
  default Map<String, Object> decorateOnWrite(
      ChatModelConfiguration configuration,
      String toolCallId,
      Map<String, Object> aiMessageAttributes) {
    return Map.of();
  }

  /**
   * Called on the read path with the already-unwrapped {@code toolCall.metadata()} (never null, may
   * be empty, untyped since it round-tripped through a process variable) of a single tool call.
   *
   * @return the entries to restore onto the outgoing {@code AiMessage.attributes()}, keyed the way
   *     LangChain4j expects.
   */
  default Map<String, Object> decorateOnRead(
      ChatModelConfiguration configuration, String toolCallId, Map<?, ?> toolCallMetadata) {
    return Map.of();
  }
}
