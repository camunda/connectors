/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client;

import io.a2a.spec.AgentCard;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientOperationConfiguration.FetchAgentCardOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientOperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientRequest;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientResult;

public class A2AClientHandlerImpl implements A2AClientHandler {

  private final AgentCardFetcher agentCardFetcher;
  private final MessageSender messageSender;

  public A2AClientHandlerImpl(AgentCardFetcher agentCardFetcher, MessageSender messageSender) {
    this.agentCardFetcher = agentCardFetcher;
    this.messageSender = messageSender;
  }

  @Override
  public A2AClientResult handle(A2AClientRequest request) {
    return switch (request.data().operation()) {
      case FetchAgentCardOperationConfiguration ignored ->
          agentCardFetcher.fetchAgentCard(request.data().connection());
      case SendMessageOperationConfiguration sendMessageOperation -> {
        AgentCard agentCard = agentCardFetcher.fetchAgentCardRaw(request.data().connection());
        yield messageSender.sendMessage(sendMessageOperation, agentCard);
      }
    };
  }
}
