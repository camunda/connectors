/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_NO_USER_MESSAGE_CONTENT;

import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.OutboundConnectorAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import org.springframework.util.CollectionUtils;

public class OutboundConnectorAgentRequestHandler
    extends BaseAgentRequestHandler<OutboundConnectorAgentExecutionContext, AgentResponse> {

  public OutboundConnectorAgentRequestHandler(
      AgentInitializer agentInitializer,
      ConversationStoreRegistry conversationStoreRegistry,
      AgentLimitsValidator limitsValidator,
      AgentMessagesHandler messagesHandler,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      AiFrameworkAdapter<?> framework,
      AgentResponseHandler responseHandler) {
    super(
        agentInitializer,
        conversationStoreRegistry,
        limitsValidator,
        messagesHandler,
        gatewayToolHandlers,
        framework,
        responseHandler);
  }

  @Override
  protected boolean modelCallPrerequisitesFulfilled(
      OutboundConnectorAgentExecutionContext executionContext,
      AgentContext agentContext,
      List<Message> addedUserMessages) {
    if (CollectionUtils.isEmpty(addedUserMessages)) {
      throw new ConnectorException(
          ERROR_CODE_NO_USER_MESSAGE_CONTENT,
          "Agent cannot proceed as no user message content (user message, tool call results) is left to add.");
    }

    return true;
  }

  @Override
  public AgentResponse completeJob(
      OutboundConnectorAgentExecutionContext executionContext,
      AgentResponse agentResponse,
      ConversationStore conversationStore) {
    return agentResponse;
  }
}
