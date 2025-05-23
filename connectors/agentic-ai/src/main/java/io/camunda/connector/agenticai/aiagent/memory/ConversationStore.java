/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory;

import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;

/**
 * Responsible for storing and loading conversation records to external systems and loading them
 * into runtime memory.
 */
public interface ConversationStore<C> {

  Class<C> conversationContextClass();

  default boolean supportsConversationContext(ConversationContext conversationContext) {
    return conversationContextClass().isInstance(conversationContext);
  }

  void loadIntoRuntimeMemory(ConversationContext conversationContext, RuntimeMemory memory);

  ConversationContext store(ConversationContext conversationContext, RuntimeMemory memory);
}
