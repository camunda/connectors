/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import java.util.Map;

/**
 * Decorates the {@code AiMessage.attributes()} / {@code AssistantMessage.metadata()} round trip
 * with provider-specific knowledge of what is safe to persist into a Camunda process variable.
 *
 * <p>{@code AiMessage.attributes()} is a generic map, populated only by langchain4j's own
 * provider-specific integration code, and is documented as "typically provider-specific". Since the
 * persisted {@code AssistantMessage} lives in an untyped process variable, it must never be dumped
 * verbatim - each provider explicitly declares what it needs echoed back.
 */
public interface AssistantMessageMetadataDecorator {

  /**
   * Called on the write path with {@code aiMessage.attributes()} (never null, may be empty).
   *
   * @return the subset that is safe and necessary to persist.
   */
  Map<String, Object> decorateOnWrite(Map<String, Object> attributes);

  /**
   * Called on the read path with the already-unwrapped {@code metadata.provider} map (never null,
   * may be empty, untyped since it round-tripped through a process variable). Returns the subset to
   * restore onto the outgoing {@code AiMessage.attributes()}.
   */
  Map<String, Object> decorateOnRead(Map<?, ?> persistedAttributes);

  AssistantMessageMetadataDecorator DROP_ATTRIBUTES =
      new AssistantMessageMetadataDecorator() {
        @Override
        public Map<String, Object> decorateOnWrite(Map<String, Object> attributes) {
          return Map.of();
        }

        @Override
        public Map<String, Object> decorateOnRead(Map<?, ?> persistedAttributes) {
          return Map.of();
        }
      };

  static AssistantMessageMetadataDecorator forProvider(
      ProviderConfiguration providerConfiguration) {
    return switch (providerConfiguration) {
      case GoogleVertexAiProviderConfiguration vertexAi ->
          GoogleVertexAiAssistantMessageMetadataDecorator.INSTANCE;
      case AnthropicProviderConfiguration anthropic -> DROP_ATTRIBUTES;
      case BedrockProviderConfiguration bedrock -> DROP_ATTRIBUTES;
      case AzureOpenAiProviderConfiguration azureOpenAi -> DROP_ATTRIBUTES;
      case OpenAiProviderConfiguration openai -> DROP_ATTRIBUTES;
      case OpenAiCompatibleProviderConfiguration openAiCompatible -> DROP_ATTRIBUTES;
    };
  }
}
