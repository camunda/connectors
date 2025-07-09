/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.api.outbound.OutboundConnectorContext;

/**
 * Responsible for storing and loading conversation records to external systems and loading them
 * into runtime memory.
 */
public interface ConversationStore {
  <T> T executeInSession(
      OutboundConnectorContext context,
      AgentRequest request,
      AgentContext agentContext,
      ConversationSessionHandler<T> sessionHandler);
}
