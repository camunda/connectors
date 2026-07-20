/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import io.camunda.connector.agenticai.aiagent.framework.langchain4j.CloseableChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;

/**
 * Factory for a single LLM provider implementation. Each provider is wrapped by a {@code
 * Langchain4JChatModelApiFactory} matching on {@link #type()}, which corresponds to the {@link
 * ProviderConfiguration} discriminator (e.g. {@code anthropic}).
 *
 * @param <T> the {@link ProviderConfiguration} subtype this provider handles
 */
public interface ChatModelProvider<T extends ProviderConfiguration> {

  /** Identifier of the provider this implementation is responsible for. */
  String type();

  /** Creates a {@link CloseableChatModel} instance from the given configuration. */
  CloseableChatModel createChatModel(T providerConfiguration);
}
