/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatModelProviderRegistry {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChatModelProviderRegistry.class);

  private final Map<String, ChatModelProvider<?>> chatModelProviders = new HashMap<>();

  public ChatModelProviderRegistry() {}

  public ChatModelProviderRegistry(List<? extends ChatModelProvider<?>> chatModelProviders) {
    chatModelProviders.forEach(this::registerChatModelProvider);
  }

  public void registerChatModelProvider(final ChatModelProvider<?> chatModelProvider) {
    final var type = chatModelProvider.type();
    if (chatModelProviders.containsKey(type)) {
      throw new IllegalArgumentException(
          "Chat model provider with type '%s' is already registered.".formatted(type));
    }

    LOGGER.debug("Registering chat model provider of type '{}'", type);

    chatModelProviders.put(type, chatModelProvider);
  }

  @SuppressWarnings("unchecked")
  public <T extends ProviderConfiguration> ChatModelProvider<T> getChatModelProvider(
      T providerConfiguration) {
    final var providerId = providerConfiguration.providerId();
    final var chatModelProvider = chatModelProviders.get(providerId);
    if (chatModelProvider == null) {
      throw new IllegalStateException(
          "No chat model provider registered for provider type: %s".formatted(providerId));
    }

    return (ChatModelProvider<T>) chatModelProvider;
  }
}
