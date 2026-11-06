/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.conversation.AgentInput;
import io.camunda.connector.agenticai.aiagent.conversation.PreviousConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.api.document.DocumentFactory;

/**
 * Composes the input of the next conversation turn from the history and the current invocation, in
 * two steps around the local tool calls, whose results it does not produce: {@link #resolve} and
 * {@link #assemble}.
 */
public interface AgentConversationTurnInputComposer {

  /**
   * Resolves the next turn. When the previous turn has tool calls, the results of its process tool
   * calls must all have arrived (or the missing ones be cancelled by an interrupting event), else
   * the result is {@link CompositionResult.Deferred}.
   *
   * @param documentFactory the document factory of the job, which restores the documents of the
   *     user prompt whose content is checked
   */
  CompositionResult resolve(
      AgentConfiguration configuration,
      AgentContext agentContext,
      PreviousConversation previousConversation,
      AgentInput agentInput,
      DocumentFactory documentFactory);

  /**
   * Assembles the input of the next turn: the tool call results of the batch in call order, or the
   * user prompt, followed by the event messages.
   *
   * <p>The changes of the agent the input asks for are not in the agent context of {@code ready}:
   * the caller applies the input to the agent context it persists ({@link TurnInput#applyTo}).
   *
   * @param documentFactory the document factory of the job, which restores the documents whose
   *     content a tool call result asks for
   * @throws IllegalStateException if a call of the batch has no result
   */
  TurnInput assemble(CompositionResult.Ready ready, DocumentFactory documentFactory);
}
