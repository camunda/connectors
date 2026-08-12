/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j;

import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.ProviderConfiguration;
import java.util.Map;

/**
 * Decorates the {@code AiMessage.attributes()} / {@code ToolCall.metadata()} round trip with
 * provider-specific knowledge of what is safe to persist into a Camunda process variable.
 *
 * <p>{@code AiMessage.attributes()} is a generic map, populated only by langchain4j's own
 * provider-specific integration code, and is documented as "typically provider-specific". Since the
 * persisted {@code ToolCall} lives in an untyped process variable, it must never be dumped verbatim
 * - every provider drops attributes entirely unless explicitly handled below.
 *
 * <p>Google's Gemini API attaches a thought signature to the specific function-call part it belongs
 * to, not to the message as a whole - langchain4j only exposes it via the flat, message-level
 * {@code AiMessage.attributes()} map keyed by tool call ID, since {@code ToolExecutionRequest} has
 * no field for it. This decorator un-flattens that back onto the individual {@link
 * io.camunda.connector.agenticai.aiagent.model.tool.ToolCall} it actually belongs to.
 *
 * <p>Persisted entries are namespaced by the provider's {@code TemplateSubType} ID (currently only
 * {@link GoogleVertexAiProviderConfiguration#GOOGLE_VERTEX_AI_ID}), so that switching a process
 * instance's provider - e.g. via a config update or a process instance migration - cannot leak a
 * previous provider's persisted metadata into an unrelated one.
 */
final class ToolCallMetadataDecorator {

  private static final String GOOGLE_THOUGHT_SIGNATURE_ATTRIBUTE_KEY_PREFIX = "thought_signature_";
  private static final String GOOGLE_THOUGHT_SIGNATURE_METADATA_KEY = "thoughtSignature";

  private ToolCallMetadataDecorator() {}

  /**
   * Called on the write path with the full, flat {@code aiMessage.attributes()} (never null, may be
   * empty) and the ID of the specific tool call being converted.
   *
   * @return the namespaced metadata to persist on that tool call.
   */
  static Map<String, Object> decorateOnWrite(
      ProviderConfiguration providerConfiguration,
      String toolCallId,
      Map<String, Object> aiMessageAttributes) {
    return switch (providerConfiguration) {
      case GoogleVertexAiProviderConfiguration ignored -> {
        if (aiMessageAttributes.get(GOOGLE_THOUGHT_SIGNATURE_ATTRIBUTE_KEY_PREFIX + toolCallId)
            instanceof String signature) {
          yield namespaced(
              GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID,
              Map.of(GOOGLE_THOUGHT_SIGNATURE_METADATA_KEY, signature));
        }
        yield Map.of();
      }
      default -> Map.of();
    };
  }

  /**
   * Called on the read path with the already-unwrapped {@code toolCall.metadata()} (never null, may
   * be empty, untyped since it round-tripped through a process variable) of a single tool call.
   * Returns the entry to restore onto the outgoing {@code AiMessage.attributes()}, keyed the way
   * langchain4j expects.
   */
  static Map<String, Object> decorateOnRead(
      ProviderConfiguration providerConfiguration, String toolCallId, Map<?, ?> toolCallMetadata) {
    return switch (providerConfiguration) {
      case GoogleVertexAiProviderConfiguration ignored -> {
        if (toolCallMetadata.get(GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID)
                instanceof Map<?, ?> googleMetadata
            && googleMetadata.get(GOOGLE_THOUGHT_SIGNATURE_METADATA_KEY)
                instanceof String signature) {
          yield Map.of(GOOGLE_THOUGHT_SIGNATURE_ATTRIBUTE_KEY_PREFIX + toolCallId, signature);
        }
        yield Map.of();
      }
      default -> Map.of();
    };
  }

  private static Map<String, Object> namespaced(String providerId, Map<String, Object> metadata) {
    return Map.of(providerId, metadata);
  }
}
