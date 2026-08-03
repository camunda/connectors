/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Decorates the {@code AiMessage.attributes()} / {@code AssistantMessage.metadata()} round trip
 * with provider-specific knowledge of what is safe to persist into a Camunda process variable.
 *
 * <p>{@code AiMessage.attributes()} is a generic map, populated only by langchain4j's own
 * provider-specific integration code, and is documented as "typically provider-specific". Since the
 * persisted {@code AssistantMessage} lives in an untyped process variable, it must never be dumped
 * verbatim - every provider drops attributes entirely unless explicitly handled below.
 */
final class AssistantMessageMetadataDecorator {

  private static final String THOUGHT_SIGNATURE_KEY_PREFIX = "thought_signature_";

  private AssistantMessageMetadataDecorator() {}

  /**
   * Called on the write path with {@code aiMessage.attributes()} (never null, may be empty).
   *
   * @return the subset that is safe and necessary to persist.
   */
  static Map<String, Object> decorateOnWrite(
      ProviderConfiguration providerConfiguration, Map<String, Object> attributes) {
    return switch (providerConfiguration) {
      case GoogleVertexAiProviderConfiguration ignored -> filterGoogleThoughtSignatures(attributes);
      default -> Map.of();
    };
  }

  /**
   * Called on the read path with the already-unwrapped {@code metadata.provider} map (never null,
   * may be empty, untyped since it round-tripped through a process variable). Returns the subset to
   * restore onto the outgoing {@code AiMessage.attributes()}.
   */
  static Map<String, Object> decorateOnRead(
      ProviderConfiguration providerConfiguration, Map<?, ?> persistedAttributes) {
    return switch (providerConfiguration) {
      case GoogleVertexAiProviderConfiguration ignored ->
          filterGoogleThoughtSignatures(persistedAttributes);
      default -> Map.of();
    };
  }

  /**
   * Gemini 3 rejects a request whose function calls are missing their {@code thoughtSignature},
   * which langchain4j's Google GenAI integration round-trips through {@code AiMessage#attributes()}
   * keyed by tool call ID (as {@code thought_signature_<toolCallId>} -> base64 string). This is the
   * only known use of {@code attributes()} for this provider, so only those entries survive the
   * round trip through the persisted process variable.
   */
  private static Map<String, Object> filterGoogleThoughtSignatures(Map<?, ?> attributes) {
    return attributes.entrySet().stream()
        .filter(
            entry ->
                entry.getKey() instanceof String key
                    && key.startsWith(THOUGHT_SIGNATURE_KEY_PREFIX))
        .filter(entry -> entry.getValue() instanceof String)
        .collect(Collectors.toMap(entry -> (String) entry.getKey(), Map.Entry::getValue));
  }
}
