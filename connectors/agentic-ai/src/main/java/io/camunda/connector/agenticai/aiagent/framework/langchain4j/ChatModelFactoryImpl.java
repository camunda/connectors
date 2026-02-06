/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import dev.langchain4j.model.chat.ChatModel;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderRegistry;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;

/**
 * Factory implementation that delegates to a ChatModelProviderRegistry.
 * This maintains backward compatibility while allowing the registry pattern.
 */
public class ChatModelFactoryImpl implements ChatModelFactory {

  private final ChatModelProviderRegistry providerRegistry;

  public ChatModelFactoryImpl(ChatModelProviderRegistry providerRegistry) {
    this.providerRegistry = providerRegistry;
  }

  @Override
  public ChatModel createChatModel(ProviderConfiguration providerConfiguration) {
    return providerRegistry.createChatModel(providerConfiguration);
  }

}
