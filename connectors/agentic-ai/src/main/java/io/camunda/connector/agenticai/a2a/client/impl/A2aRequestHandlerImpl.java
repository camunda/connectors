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
import io.camunda.connector.agenticai.a2a.client.model.A2aOperationConfiguration.FetchAgentCardOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2aOperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2aRequest;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aResult;

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
    return switch (request.data().operation()) {
      case FetchAgentCardOperationConfiguration ignored ->
          agentCardFetcher.fetchAgentCard(request.data().connection());
      case SendMessageOperationConfiguration sendMessageOperation -> {
        AgentCard agentCard = agentCardFetcher.fetchAgentCardRaw(request.data().connection());
        yield a2aMessageSender.sendMessage(agentCard, sendMessageOperation);
      }
    };
  }
}
