/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.AiAgentTaskConnectorResponse;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.OutboundConnectorAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.api.error.ConnectorException;

public class OutboundConnectorAgentRequestHandler
    extends BaseAgentRequestHandler<
        OutboundConnectorAgentExecutionContext, AiAgentTaskConnectorResponse> {

  public OutboundConnectorAgentRequestHandler(
      AgentInitializer agentInitializer,
      ConversationStoreRegistry conversationStoreRegistry,
      ConversationTurnComposer agentInputComposer,
      AiFrameworkAdapter<?> framework,
      SystemPromptComposer systemPromptComposer,
      AgentResponseHandler responseHandler,
      AgentInstanceClient agentInstanceClient) {
    super(
        agentInitializer,
        conversationStoreRegistry,
        agentInputComposer,
        framework,
        systemPromptComposer,
        responseHandler,
        agentInstanceClient);
  }

  @Override
  protected boolean shouldUpdateAgentInstanceBeforeJobCompletion(AgentConversation conversation) {
    return true;
  }

  @Override
  protected AiAgentTaskConnectorResponse handleNoInput(
      OutboundConnectorAgentExecutionContext executionContext) {
    throw new ConnectorException(
        AgentErrorCodes.ERROR_CODE_NO_USER_MESSAGE_CONTENT,
        "Agent cannot proceed as no user message content (user message, tool call results) is left to add.");
  }

  @Override
  public AiAgentTaskConnectorResponse buildConnectorResponse(
      OutboundConnectorAgentExecutionContext executionContext,
      AgentConversation conversation,
      AgentResponse agentResponse,
      AgentJobCompletionListener completionListener) {
    return new AiAgentTaskConnectorResponse(agentResponse, completionListener);
  }
}
