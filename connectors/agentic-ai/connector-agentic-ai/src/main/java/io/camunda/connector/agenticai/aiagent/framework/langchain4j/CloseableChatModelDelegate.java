/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pairs a {@link ChatModel} with an {@link AutoCloseable} resource (e.g. a {@code
 * BedrockRuntimeClient}) so both can be treated as a single {@link CloseableChatModel}. {@link
 * #close()} releases the resource and swallows any exception to avoid masking LLM failures in the
 * caller's {@code finally} block.
 */
public record CloseableChatModelDelegate(ChatModel delegate, AutoCloseable resource)
    implements CloseableChatModel {

  private static final Logger LOGGER = LoggerFactory.getLogger(CloseableChatModelDelegate.class);

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
}
