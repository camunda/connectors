/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import dev.langchain4j.model.chat.ChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.CustomProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for managing ChatModelProvider implementations.
 * Allows registration of custom providers and looks up the appropriate provider
 * for a given configuration.
 */
public class ChatModelProviderRegistry {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChatModelProviderRegistry.class);

  private final Map<String, ChatModelProvider> providers = new HashMap<>();

  public ChatModelProviderRegistry() {}

  public ChatModelProviderRegistry(List<ChatModelProvider> providers) {
    providers.forEach(this::registerProvider);
  }

  /**
   * Registers a ChatModelProvider implementation.
   *
   * @param provider the provider to register
   * @throws IllegalArgumentException if a provider with the same type is already registered
   */
  public void registerProvider(ChatModelProvider provider) {
    final var type = provider.getProviderType();
    if (providers.containsKey(type)) {
      throw new IllegalArgumentException(
          "Chat model provider with type '%s' is already registered".formatted(type));
    }

    LOGGER.debug("Registering chat model provider of type '{}'", type);
    providers.put(type, provider);
  }

  /**
   * Creates a ChatModel instance from the given provider configuration.
   * For built-in providers, uses the registered provider implementation.
   * For custom providers, looks up the provider by the custom providerType.
   *
   * @param providerConfiguration the configuration for the provider
   * @return a configured ChatModel instance
   * @throws IllegalStateException if no provider is found for the configuration
   */
  public ChatModel createChatModel(ProviderConfiguration providerConfiguration) {
    ChatModelProvider provider;

    if (providerConfiguration instanceof CustomProviderConfiguration customConfig) {
      // For custom configurations, look up by the custom providerType
      provider = providers.get(customConfig.providerType());
      if (provider == null) {
        throw new IllegalStateException(
            "No chat model provider registered for custom provider type: %s"
                .formatted(customConfig.providerType()));
      }
    } else {
      // For built-in providers, look up by the configuration class type
      provider = providers.values().stream()
          .filter(p -> p.supports(providerConfiguration))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException(
              "No chat model provider found for configuration type: %s"
                  .formatted(providerConfiguration.getClass().getSimpleName())));
    }

    return provider.createChatModel(providerConfiguration);
  }
}
