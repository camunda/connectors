/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.impl;

import io.a2a.spec.AgentCard;
import io.camunda.connector.agenticai.a2a.client.api.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.api.A2aMessageSender;
import io.camunda.connector.agenticai.a2a.client.api.A2aRequestHandler;
import io.camunda.connector.agenticai.a2a.client.model.A2aConnectorModeConfiguration.StandaloneModeConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2aConnectorModeConfiguration.ToolModeConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2aRequest;
import io.camunda.connector.agenticai.a2a.client.model.A2aStandaloneOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2aStandaloneOperationConfiguration.FetchAgentCardOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2aStandaloneOperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2aToolOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aResult;
import java.util.List;

public class A2aRequestHandlerImpl implements A2aRequestHandler {

  private final A2aAgentCardFetcher agentCardFetcher;
  private final A2aMessageSender a2aMessageSender;

  public A2aRequestHandlerImpl(
      A2aAgentCardFetcher agentCardFetcher, A2aMessageSender a2aMessageSender) {
    this.agentCardFetcher = agentCardFetcher;
    this.a2aMessageSender = a2aMessageSender;
  }

  @Override
  public A2aResult handle(A2aRequest request) {
    A2aStandaloneOperationConfiguration operation;
    switch (request.data().connectorMode()) {
      case StandaloneModeConfiguration standaloneOperationConfiguration ->
          operation = standaloneOperationConfiguration.operation();
      case ToolModeConfiguration toolOperationConfiguration ->
          operation = convertOperation(toolOperationConfiguration.toolOperation());
      default ->
          throw new IllegalArgumentException(
              "Unsupported connectorMode: " + request.data().connectorMode());
    }
    return switch (operation) {
      case FetchAgentCardOperationConfiguration ignored ->
          agentCardFetcher.fetchAgentCard(request.data().connection());
      case SendMessageOperationConfiguration sendMessageOperation -> {
        AgentCard agentCard = agentCardFetcher.fetchAgentCardRaw(request.data().connection());
        yield a2aMessageSender.sendMessage(agentCard, sendMessageOperation);
      }
    };
  }

  private A2aStandaloneOperationConfiguration convertOperation(
      A2aToolOperationConfiguration operation) {
    switch (operation.operation()) {
      case FetchAgentCardOperationConfiguration.FETCH_AGENT_CARD_ID -> {
        return new FetchAgentCardOperationConfiguration();
      }
      case SendMessageOperationConfiguration.SEND_MESSAGE_ID -> {
        if (operation.params() == null || !operation.params().containsKey("message")) {
          throw new IllegalArgumentException(
              "The 'message' parameter is required for the '%s' operation."
                  .formatted(operation.operation()));
        }
        return new SendMessageOperationConfiguration(
            new SendMessageOperationConfiguration.Parameters(
                operation.params().get("message").toString(), List.of()),
            operation.timeout());
      }
      default ->
          throw new IllegalArgumentException(
              "Unsupported operation: '%s'".formatted(operation.operation()));
    }
  }
}
