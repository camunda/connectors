/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.api.outbound.JobCompletionFailure;

/**
 * Pluggable backend for persisting and loading conversation history.
 *
 * <p>All implementations must follow the <b>write-ahead with pointer-based visibility</b> contract:
 * the {@link ConversationContext} returned by {@link ConversationSession#storeMessages} acts as a
 * pointer to the stored data. That pointer only becomes authoritative once Zeebe accepts the job
 * completion containing the updated {@code AgentContext}. If job completion fails, the retry uses
 * the old pointer — so newly written data must never overwrite or mutate what the old pointer
 * resolves to.
 *
 * @see ConversationSession
 * @see ConversationContext
 */
public interface ConversationStore {
  String type();

  /**
   * Creates a new session for a single agent turn. The caller manages the session lifecycle via
   * try-with-resources. Implementations may allocate external resources (connections, clients)
   * here; they will be released when {@link ConversationSession#close()} is called.
   */
  ConversationSession createSession(
      AgentExecutionContext executionContext, AgentContext agentContext);

  /**
   * Best-effort: the job carrying the given agentContext was accepted by Zeebe. Implementations may
   * use this to clean up previous state (old documents, orphaned branches).
   */
  default void onJobCompleted(AgentContext committedContext) {}

  /**
   * Best-effort: the job was not accepted. The store may have written state that Zeebe doesn't know
   * about.
   */
  default void onJobCompletionFailed(AgentContext failedContext, JobCompletionFailure failure) {}
}
