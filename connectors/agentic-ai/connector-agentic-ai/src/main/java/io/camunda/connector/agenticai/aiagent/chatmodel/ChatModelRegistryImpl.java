/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Resolves the {@link ChatModel} for a given {@link ChatModelConfiguration} by asking every
 * registered {@link ChatModelFactory} whether it {@link ChatModelFactory#supports supports} the
 * configuration and routing to the single factory that does. Configurations supported by no
 * factory, or by more than one, fail loud rather than being resolved implicitly.
 */
public class ChatModelRegistryImpl implements ChatModelRegistry {

  private final List<ChatModelFactory> factories;

  public ChatModelRegistryImpl(List<ChatModelFactory> factories) {
    this.factories = List.copyOf(factories);
  }

  @Override
  public ChatModel resolve(ChatModelConfiguration configuration) {
    final var matches =
        factories.stream().filter(factory -> factory.supports(configuration)).toList();

    if (matches.isEmpty()) {
      throw new IllegalArgumentException(
          "No chat model registered for configuration: " + configuration);
    }

    if (matches.size() > 1) {
      final var factoryNames =
          matches.stream()
              .map(factory -> factory.getClass().getSimpleName())
              .collect(Collectors.joining(", "));
      throw new IllegalStateException(
          "Multiple chat model factories match configuration: %s (matched factories: %s)"
              .formatted(configuration, factoryNames));
    }

    return matches.get(0).create(configuration);
  }
}
