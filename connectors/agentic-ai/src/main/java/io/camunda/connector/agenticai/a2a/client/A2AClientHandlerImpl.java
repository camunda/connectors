/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client;

import io.a2a.spec.AgentCard;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientOperationConfiguration.FetchAgentCardOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientOperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientRequest;
import io.camunda.connector.agenticai.a2a.client.model.ConnectorModeConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.ToolModeOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientResult;
import java.util.List;

public class A2AClientHandlerImpl implements A2AClientHandler {

  private final AgentCardFetcher agentCardFetcher;
  private final MessageSender messageSender;

  public A2AClientHandlerImpl(AgentCardFetcher agentCardFetcher, MessageSender messageSender) {
    this.agentCardFetcher = agentCardFetcher;
    this.messageSender = messageSender;
  }

  @Override
  public A2AClientResult handle(A2AClientRequest request) {
    A2AClientOperationConfiguration operation;
    switch (request.data().connectorModeConfiguration()) {
      case ConnectorModeConfiguration.StandaloneModeConfiguration
                  standaloneOperationConfiguration ->
          operation = standaloneOperationConfiguration.operation();
      case ConnectorModeConfiguration.ToolModeConfiguration toolOperationConfiguration ->
          operation = convertOperation(toolOperationConfiguration.toolOperation());
      default ->
          throw new IllegalArgumentException(
              "Unsupported connectorMode: " + request.data().connectorModeConfiguration());
    }
    return switch (operation) {
      case FetchAgentCardOperationConfiguration ignored ->
          agentCardFetcher.fetchAgentCard(request.data().connection());
      case SendMessageOperationConfiguration sendMessageOperation -> {
        AgentCard agentCard = agentCardFetcher.fetchAgentCardRaw(request.data().connection());
        yield messageSender.sendMessage(sendMessageOperation, agentCard);
      }
    };
  }

  private A2AClientOperationConfiguration convertOperation(
      ToolModeOperationConfiguration operation) {
    switch (operation.operation()) {
      case FetchAgentCardOperationConfiguration.FETCH_AGENT_CARD_ID -> {
        return new FetchAgentCardOperationConfiguration();
      }
      case SendMessageOperationConfiguration.SEND_MESSAGE_ID -> {
        if (operation.params() == null || !operation.params().containsKey("message")) {
          throw new IllegalArgumentException(
              "The 'message' parameter is required for the '%s' connectorMode."
                  .formatted(operation.operation()));
        }
        return new SendMessageOperationConfiguration(
            new SendMessageOperationConfiguration.Parameters(
                operation.params().get("message").toString(), List.of()),
            operation.timeout());
      }
      default ->
          throw new IllegalArgumentException(
              "Unsupported connectorMode: '%s'".formatted(operation.operation()));
    }
  }
}
