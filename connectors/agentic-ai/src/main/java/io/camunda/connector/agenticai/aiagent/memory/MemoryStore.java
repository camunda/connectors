/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;

public interface MemoryStore {
  void loadIntoMemory(AgentContext agentContext, ConversationMemory memory);

  AgentContext store(AgentContext agentContext, ConversationMemory memory);
}
