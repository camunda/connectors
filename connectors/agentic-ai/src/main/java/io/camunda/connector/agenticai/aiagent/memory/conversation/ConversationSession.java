/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;

public interface ConversationSession extends AutoCloseable {

  ConversationLoadResult loadMessages(AgentContext agentContext);

  /**
   * Stores messages and returns an updated ConversationContext (storage cursor). The caller is
   * responsible for assembling the full AgentContext via {@code
   * agentContext.withConversation(returnedContext)}.
   */
  ConversationContext storeMessages(AgentContext agentContext, ConversationStoreRequest request);

  @Override
  default void close() {}
}
