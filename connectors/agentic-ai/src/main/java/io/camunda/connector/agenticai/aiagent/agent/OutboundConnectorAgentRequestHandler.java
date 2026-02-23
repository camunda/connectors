/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_NO_USER_MESSAGE_CONTENT;

import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.OutboundConnectorAgentExecutionContext;
import io.camunda.connector.api.error.ConnectorException;

public class OutboundConnectorAgentRequestHandler
    extends BaseAgentRequestHandler<OutboundConnectorAgentExecutionContext, AgentResponse> {

  public OutboundConnectorAgentRequestHandler(
      AgentInitializer agentInitializer, AgentExecutor agentExecutor) {
    super(agentInitializer, agentExecutor);
  }

  @Override
  public AgentResponse completeJob(
      OutboundConnectorAgentExecutionContext executionContext,
      AgentResponse agentResponse,
      ConversationSession session) {
    if (agentResponse == null) {
      if (session != null) {
        session.close();
      }
      throw new ConnectorException(
          ERROR_CODE_NO_USER_MESSAGE_CONTENT,
          "Agent cannot proceed as no user message content (user message, tool call results) is left to add.");
    }

    if (session != null) {
      try {
        session.onJobCompleted(agentResponse.context());
      } finally {
        session.close();
      }
    }
    return agentResponse;
  }
}
