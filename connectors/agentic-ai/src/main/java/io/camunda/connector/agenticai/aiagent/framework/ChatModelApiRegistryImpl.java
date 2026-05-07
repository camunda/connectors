/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework;

import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiRegistry;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatModelApiRegistryImpl implements ChatModelApiRegistry {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChatModelApiRegistryImpl.class);

  private final Map<String, ChatModelApiFactory<?>> factoriesByProviderType;

  public ChatModelApiRegistryImpl(List<? extends ChatModelApiFactory<?>> factories) {
    this.factoriesByProviderType = indexByProviderType(factories);
  }

  private static Map<String, ChatModelApiFactory<?>> indexByProviderType(
      List<? extends ChatModelApiFactory<?>> factories) {
    final Map<String, ChatModelApiFactory<?>> index = new HashMap<>();
    for (ChatModelApiFactory<?> factory : factories) {
      for (String providerType : factory.supportedProviderTypes()) {
        final var existing = index.get(providerType);
        if (existing != null) {
          throw new IllegalStateException(
              "Two chat model API factories claim provider type '%s': %s and %s. To override the default factory, exclude the built-in bean (e.g. via @ConditionalOnMissingBean) before contributing your own."
                  .formatted(
                      providerType, existing.getClass().getName(), factory.getClass().getName()));
        }
        index.put(providerType, factory);
        LOGGER.debug(
            "Registered chat model API factory for provider type '{}': {} (apiFamily={})",
            providerType,
            factory.getClass().getName(),
            factory.apiFamily());
      }
    }
    return Map.copyOf(index);
  }

  @Override
  @SuppressWarnings("unchecked")
  public ChatModelApi resolve(ProviderConfiguration configuration) {
    final var providerType = configuration.providerType();
    final var factory = factoriesByProviderType.get(providerType);
    if (factory == null) {
      throw new IllegalStateException(
          "No chat model API factory registered for provider type '%s'".formatted(providerType));
    }
    return ((ChatModelApiFactory<ProviderConfiguration>) factory).create(configuration);
  }
}
