/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess;

import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSessionHandler;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.api.outbound.OutboundConnectorContext;

public class InProcessConversationStore implements ConversationStore {

  @Override
  public <T> T executeInSession(
      OutboundConnectorContext context,
      AgentRequest request,
      AgentContext agentContext,
      ConversationSessionHandler<T> sessionHandler) {
    return sessionHandler.handleSession(new InProcessConversationSession());
  }
}
