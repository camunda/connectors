/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.CloseableChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pairs a {@link ChatModel} with an {@link AutoCloseable} resource, like {@link
 * io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.CloseableChatModelDelegate},
 * plus the Google Vertex AI specific tool call metadata decoration.
 *
 * <p>Google's Gemini API attaches a thought signature to the specific function-call part it belongs
 * to, not to the message as a whole - langchain4j only exposes it via the flat, message-level
 * {@code AiMessage.attributes()} map keyed by tool call ID, since {@code ToolExecutionRequest} has
 * no field for it. {@link #decorateOnWrite}/{@link #decorateOnRead} un-flatten that back onto the
 * individual {@link io.camunda.connector.agenticai.aiagent.model.tool.ToolCall} it actually belongs
 * to.
 *
 * <p>Persisted entries are namespaced by the provider's {@code TemplateSubType} ID so that
 * switching a process instance's provider - e.g. via a config update or a process instance
 * migration - cannot leak a previous provider's persisted metadata into an unrelated one.
 */
public record GoogleVertexAiCloseableChatModel(ChatModel delegate, AutoCloseable resource)
    implements CloseableChatModel {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(GoogleVertexAiCloseableChatModel.class);

  private static final String THOUGHT_SIGNATURE_ATTRIBUTE_KEY_PREFIX = "thought_signature_";
  private static final String THOUGHT_SIGNATURE_METADATA_KEY = "thoughtSignature";

  @Override
  public ChatResponse chat(ChatRequest request) {
    return delegate.chat(request);
  }

  @Override
  public ChatRequestParameters defaultRequestParameters() {
    return delegate.defaultRequestParameters();
  }

  @Override
  public List<ChatModelListener> listeners() {
    return delegate.listeners();
  }

  @Override
  public ModelProvider provider() {
    return delegate.provider();
  }

  @Override
  public Set<Capability> supportedCapabilities() {
    return delegate.supportedCapabilities();
  }

  @Override
  public void close() {
    try {
      resource.close();
    } catch (Exception e) {
      LOGGER.warn("Failed to close chat model resource", e);
    }
  }

  @Override
  public Map<String, Object> decorateOnWrite(
      String toolCallId, Map<String, Object> aiMessageAttributes) {
    if (aiMessageAttributes.get(THOUGHT_SIGNATURE_ATTRIBUTE_KEY_PREFIX + toolCallId)
        instanceof String signature) {
      return Map.of(
          GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID,
          Map.of(THOUGHT_SIGNATURE_METADATA_KEY, signature));
    }
    return Map.of();
  }

  @Override
  public Map<String, Object> decorateOnRead(String toolCallId, Map<?, ?> toolCallMetadata) {
    if (toolCallMetadata.get(GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID)
            instanceof Map<?, ?> googleMetadata
        && googleMetadata.get(THOUGHT_SIGNATURE_METADATA_KEY) instanceof String signature) {
      return Map.of(THOUGHT_SIGNATURE_ATTRIBUTE_KEY_PREFIX + toolCallId, signature);
    }
    return Map.of();
  }
}
