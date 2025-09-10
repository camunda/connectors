/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.a2a.A2A;
import io.a2a.spec.A2AClientError;
import io.a2a.spec.AgentCard;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientRequest;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientRequest.A2AClientRequestData.ConnectionConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.OperationConfiguration.FetchAgentCardOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.OperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientAgentCardResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientResult;
import java.util.Collections;
import org.apache.commons.collections4.CollectionUtils;

public class A2AClientHandlerImpl implements A2AClientHandler {

  @Override
  public A2AClientResult handle(A2AClientRequest request) {
    return switch (request.data().operation()) {
      case FetchAgentCardOperationConfiguration ignored ->
          fetchAgentCard(request.data().connection());
      case SendMessageOperationConfiguration sendMessage -> null;
    };
  }

  private A2AClientAgentCardResult fetchAgentCard(ConnectionConfiguration connection) {
    final var relativeCardPath =
        isNotBlank(connection.agentCardLocation()) ? connection.agentCardLocation() : null;
    try {
      AgentCard agentCard =
          A2A.getAgentCard(connection.url(), relativeCardPath, Collections.emptyMap());
      return convertAgentCard(agentCard);
    } catch (A2AClientError e) {
      throw new RuntimeException(e);
    }
  }

  private A2AClientAgentCardResult convertAgentCard(AgentCard agentCard) {
    final var agentSkills =
        agentCard.skills().stream()
            .map(
                agentSkill ->
                    new A2AClientAgentCardResult.AgentSkill(
                        agentSkill.id(),
                        agentSkill.name(),
                        agentSkill.description(),
                        agentSkill.tags(),
                        agentSkill.examples(),
                        CollectionUtils.isEmpty(agentSkill.inputModes())
                            ? agentCard.defaultInputModes()
                            : agentSkill.inputModes(),
                        CollectionUtils.isEmpty(agentSkill.outputModes())
                            ? agentCard.defaultOutputModes()
                            : agentSkill.outputModes()))
            .toList();
    return new A2AClientAgentCardResult(agentCard.name(), agentCard.description(), agentSkills);
  }
}
