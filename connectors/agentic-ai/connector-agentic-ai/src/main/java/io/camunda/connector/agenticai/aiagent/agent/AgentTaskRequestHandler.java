/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.AgentTaskConnectorResponse;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentTaskExecutionContext;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.api.error.ConnectorException;
import org.jspecify.annotations.Nullable;

public class AgentTaskRequestHandler
    extends BaseAgentRequestHandler<AgentTaskExecutionContext, AgentTaskConnectorResponse> {

  public AgentTaskRequestHandler(
      AgentInitializer agentInitializer,
      ConversationStoreRegistry conversationStoreRegistry,
      AgentConversationTurnInputComposer agentInputComposer,
      ChatModelRegistry chatModelRegistry,
      SystemPromptComposer systemPromptComposer,
      AgentResponseHandler responseHandler,
      AgentInstanceClient agentInstanceClient) {
    super(
        agentInitializer,
        conversationStoreRegistry,
        agentInputComposer,
        chatModelRegistry,
        systemPromptComposer,
        responseHandler,
        agentInstanceClient);
  }

  @Override
  protected boolean shouldUpdateAgentInstanceBeforeJobCompletion(AgentConversation conversation) {
    return true;
  }

  @Override
  protected AgentTaskConnectorResponse handleNoInput(AgentTaskExecutionContext executionContext) {
    throw new ConnectorException(
        AgentErrorCodes.ERROR_CODE_NO_USER_MESSAGE_CONTENT,
        "Agent cannot proceed as no user message content (user message, tool call results) is left to add.");
  }

  @Override
  public AgentTaskConnectorResponse buildConnectorResponse(
      AgentTaskExecutionContext executionContext,
      @Nullable AgentConversation conversation,
      @Nullable AgentResponse agentResponse,
      @Nullable AgentJobCompletionListener completionListener) {
    return new AgentTaskConnectorResponse(agentResponse, completionListener);
  }
}
