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
 * Interface for creating ChatModel instances from provider configurations.
 * Implementations are responsible for building and configuring ChatModel instances
 * for specific LLM providers.
 */
public interface ChatModelProvider {

  /**
   * Returns the provider type that this implementation supports.
   * This should match the type constant in the corresponding ProviderConfiguration.
   *
   * @return the provider type identifier
   */
  String getProviderType();

  /**
   * Creates a ChatModel instance from the given provider configuration.
   *
   * @param providerConfiguration the configuration for the provider
   * @return a configured ChatModel instance
   * @throws IllegalArgumentException if the configuration is not supported
   */
  ChatModel createChatModel(ProviderConfiguration providerConfiguration);

  /**
   * Checks if this provider supports the given configuration.
   *
   * @param providerConfiguration the configuration to check
   * @return true if this provider can handle the configuration
   */
  default boolean supports(ProviderConfiguration providerConfiguration) {
    return false;
  }
}
