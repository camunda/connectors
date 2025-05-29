/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory;

import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;

/**
 * Responsible for storing and loading conversation records to external systems and loading them
 * into runtime memory.
 */
public interface ConversationStore<C extends ConversationContext> {

  Class<C> conversationContextClass();

  void loadIntoRuntimeMemory(
      OutboundConnectorContext context, AgentContext agentContext, RuntimeMemory memory);

  AgentContext storeFromRuntimeMemory(
      OutboundConnectorContext context, AgentContext agentContext, RuntimeMemory memory);
}
