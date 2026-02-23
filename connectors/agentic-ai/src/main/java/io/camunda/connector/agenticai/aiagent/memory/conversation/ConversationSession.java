/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.model.message.Message;
import java.util.List;

public interface ConversationSession extends AutoCloseable {

  ConversationLoadResult loadMessages(AgentContext agentContext);

  AgentContext storeMessages(AgentContext agentContext, List<Message> messages);

  /**
   * Called after completeJob succeeds. Use for post-success cleanup like deleting previous document
   * versions or pruning conversation history.
   */
  default void onJobCompleted(AgentContext agentContext) {}

  /**
   * Called when the conversation is no longer needed (e.g., process completed). Use for final
   * cleanup of all conversation artifacts.
   */
  default void cleanup(AgentContext agentContext) {}

  /** Release any resources held by this session (e.g., SDK clients, connections). */
  @Override
  default void close() {}
}
