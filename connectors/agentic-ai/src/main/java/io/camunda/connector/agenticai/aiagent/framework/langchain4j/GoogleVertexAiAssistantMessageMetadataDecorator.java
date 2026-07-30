/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gemini 3 rejects a request whose function calls are missing their {@code thoughtSignature}, which
 * langchain4j's Google GenAI integration round-trips through {@code AiMessage#attributes()} keyed
 * by tool call ID (as {@code thought_signature_<toolCallId>} -> base64 string). This is the only
 * known use of {@code attributes()} for this provider, so only those entries survive the round trip
 * through the persisted process variable.
 */
final class GoogleVertexAiAssistantMessageMetadataDecorator
    implements AssistantMessageMetadataDecorator {

  static final AssistantMessageMetadataDecorator INSTANCE =
      new GoogleVertexAiAssistantMessageMetadataDecorator();

  private static final String THOUGHT_SIGNATURE_KEY_PREFIX = "thought_signature_";

  private GoogleVertexAiAssistantMessageMetadataDecorator() {}

  @Override
  public Map<String, Object> decorateOnWrite(Map<String, Object> attributes) {
    return filterThoughtSignatures(attributes);
  }

  @Override
  public Map<String, Object> decorateOnRead(Map<?, ?> persistedAttributes) {
    return filterThoughtSignatures(persistedAttributes);
  }

  private Map<String, Object> filterThoughtSignatures(Map<?, ?> attributes) {
    return attributes.entrySet().stream()
        .filter(
            entry ->
                entry.getKey() instanceof String key
                    && key.startsWith(THOUGHT_SIGNATURE_KEY_PREFIX))
        .filter(entry -> entry.getValue() instanceof String)
        .collect(Collectors.toMap(entry -> (String) entry.getKey(), Map.Entry::getValue));
  }
}
