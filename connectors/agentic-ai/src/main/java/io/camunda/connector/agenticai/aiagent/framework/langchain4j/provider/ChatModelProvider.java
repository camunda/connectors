/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import dev.langchain4j.model.chat.ChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;

/**
 * Factory for a single LLM provider implementation. Implementations are registered in the {@link
 * ChatModelProviderRegistry} by their {@link #type()}.
 *
 * <p>Built-in providers are registered with the same id as the corresponding {@link
 * ProviderConfiguration} discriminator (e.g. {@code anthropic}). Custom providers can be registered
 * under an arbitrary id and resolved through {@link
 * io.camunda.connector.agenticai.aiagent.model.request.provider.CustomProviderConfiguration}.
 */
public interface ChatModelProvider {

  /** Identifier of the provider this implementation is responsible for. */
  String type();

  /** Creates a {@link ChatModel} instance from the given configuration. */
  ChatModel createChatModel(ProviderConfiguration providerConfiguration);
}
