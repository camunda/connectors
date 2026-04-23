/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;

/**
 * A per-turn session for loading and storing conversation messages. Created by {@link
 * ConversationStore#createSession} and managed via try-with-resources.
 *
 * <p>Implementations must follow the write-ahead with pointer-based visibility contract described
 * on {@link ConversationStore}: {@link #storeMessages} must write to a <b>new</b> location
 * (version, snapshot, branch) and return a new {@link ConversationContext} pointing to it. It must
 * never mutate or overwrite the data that the previous context points to.
 *
 * @see ConversationStore
 */
public interface ConversationSession extends AutoCloseable {

  /**
   * Loads the conversation history for the current agent context. The returned messages are those
   * referenced by the {@link ConversationContext} in the given {@code agentContext} — not "latest"
   * or "most recent", which would break retry safety.
   *
   * @return the loaded messages, or {@link ConversationLoadResult#empty()} if no previous
   *     conversation exists
   */
  ConversationLoadResult loadMessages(AgentContext agentContext);

  /**
   * Persists the full message list and returns an updated {@link ConversationContext} (storage
   * cursor) pointing to the newly written data. The caller is responsible for assembling the full
   * {@code AgentContext} via {@code agentContext.withConversation(returnedContext)}.
   *
   * <p>Must write to a new location — never overwrite the data referenced by the current context.
   */
  ConversationContext storeMessages(AgentContext agentContext, ConversationStoreRequest request);

  @Override
  default void close() {}
}
