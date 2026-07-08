/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework;

import io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiRegistry;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Comparator;
import java.util.List;

/**
 * Resolves the {@link ChatModelApi} for a given {@link ChatModelApiConfiguration} by asking the
 * registered {@link ChatModelApiFactory} instances, in {@link ChatModelApiFactory#getOrder()}
 * precedence, which of them supports it.
 */
public class ChatModelApiRegistryImpl implements ChatModelApiRegistry {

  private final List<ChatModelApiFactory> factories;

  public ChatModelApiRegistryImpl(List<ChatModelApiFactory> factories) {
    this.factories =
        factories.stream().sorted(Comparator.comparingInt(ChatModelApiFactory::getOrder)).toList();
  }

  @Override
  public ChatModelApi resolve(ChatModelApiConfiguration configuration) {
    return factories.stream()
        .filter(factory -> factory.supports(configuration))
        .findFirst()
        .orElseThrow(
            () ->
                new ConnectorException(
                    AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL,
                    "No chat model registered for configuration: " + configuration))
        .create(configuration);
  }
}
