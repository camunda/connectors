/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel;

import io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Resolves the {@link ChatModelApi} for a given {@link ChatModelApiConfiguration} by asking every
 * registered {@link ChatModelApiFactory} whether it {@link ChatModelApiFactory#supports supports}
 * the configuration and routing to the single factory that does. Configurations supported by no
 * factory, or by more than one, fail loud rather than being resolved implicitly.
 */
public class ChatModelApiRegistryImpl implements ChatModelApiRegistry {

  private final List<ChatModelApiFactory> factories;

  public ChatModelApiRegistryImpl(List<ChatModelApiFactory> factories) {
    this.factories = List.copyOf(factories);
  }

  @Override
  public ChatModelApi resolve(ChatModelApiConfiguration configuration) {
    final var matches =
        factories.stream().filter(factory -> factory.supports(configuration)).toList();

    if (matches.isEmpty()) {
      throw new ConnectorException(
          AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL,
          "No chat model registered for configuration: " + configuration);
    }

    if (matches.size() > 1) {
      final var factoryNames =
          matches.stream()
              .map(factory -> factory.getClass().getSimpleName())
              .collect(Collectors.joining(", "));
      throw new ConnectorException(
          AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL,
          "Multiple chat model factories match configuration: %s (matched factories: %s)"
              .formatted(configuration, factoryNames));
    }

    return matches.get(0).create(configuration);
  }
}
