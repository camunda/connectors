/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import dev.langchain4j.model.chat.ChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiBackend;

/**
 * Dispatching {@link ChatModelProvider} for the unified {@link OpenAiProviderConfiguration}. Routes
 * each request to the appropriate backend-specific provider:
 *
 * <ul>
 *   <li>{@link OpenAiBackend#OPENAI} → {@link OpenAiChatModelProvider}
 *   <li>{@link OpenAiBackend#FOUNDRY} → {@link AzureOpenAiChatModelProvider}
 *   <li>{@link OpenAiBackend#CUSTOM} → {@link OpenAiCompatibleChatModelProvider}
 * </ul>
 *
 * <p>This bean is registered as the single {@code ChatModelProvider<OpenAiProviderConfiguration>}
 * in the Spring context and acts as the LangChain4j fallback when the native OpenAI SDK factory is
 * not on the classpath (or is excluded).
 */
public class OpenAiDispatchingChatModelProvider
    implements ChatModelProvider<OpenAiProviderConfiguration> {

  private final OpenAiChatModelProvider openAiProvider;
  private final AzureOpenAiChatModelProvider azureOpenAiProvider;
  private final OpenAiCompatibleChatModelProvider openAiCompatibleProvider;

  public OpenAiDispatchingChatModelProvider(
      OpenAiChatModelProvider openAiProvider,
      AzureOpenAiChatModelProvider azureOpenAiProvider,
      OpenAiCompatibleChatModelProvider openAiCompatibleProvider) {
    this.openAiProvider = openAiProvider;
    this.azureOpenAiProvider = azureOpenAiProvider;
    this.openAiCompatibleProvider = openAiCompatibleProvider;
  }

  @Override
  public String type() {
    return OpenAiProviderConfiguration.OPENAI_ID;
  }

  @Override
  public ChatModel createChatModel(OpenAiProviderConfiguration providerConfiguration) {
    final var backend = providerConfiguration.openai().backend();
    return switch (backend) {
      case OPENAI -> openAiProvider.createChatModel(providerConfiguration);
      case FOUNDRY -> azureOpenAiProvider.createChatModel(providerConfiguration);
      case CUSTOM -> openAiCompatibleProvider.createChatModel(providerConfiguration);
    };
  }
}
