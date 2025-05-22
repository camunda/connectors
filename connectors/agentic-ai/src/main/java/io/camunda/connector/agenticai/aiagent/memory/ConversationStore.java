/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;

/**
 * Responsible for storing and loading conversation records to external systems and loading them
 * into conversation memory.
 */
public interface ConversationStore<C> {

  Class<C> conversationRecordClass();

  default boolean supportsRecord(ConversationRecord conversationRecord) {
    return conversationRecordClass().isInstance(conversationRecord);
  }

  void loadFromContext(AgentContext agentContext, ConversationMemory memory);

  ConversationRecord store(AgentContext agentContext, ConversationMemory memory);

  default AgentContext storeToContext(AgentContext agentContext, ConversationMemory memory) {
    ConversationRecord conversationRecord = store(agentContext, memory);
    return agentContext.withConversation(conversationRecord);
  }
}
