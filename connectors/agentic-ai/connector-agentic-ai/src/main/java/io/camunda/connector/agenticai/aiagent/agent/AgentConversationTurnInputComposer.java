/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.agent.RuntimeToolCallAnswers.RuntimeToolCallAnswer;
import io.camunda.connector.agenticai.aiagent.agent.RuntimeToolCallHandler.RuntimeToolCallSplit;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentInput;
import io.camunda.connector.agenticai.aiagent.model.PreviousConversation;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Composes the next conversation turn input from raw history and the current invocation state. */
public interface AgentConversationTurnInputComposer {

  /**
   * Composes the next turn input.
   *
   * @param deferredCalls the calls of the last assistant message of {@code previousConversation}:
   *     the calls the agent answers itself are answered when the tool call results of the turn are
   *     composed, in the order of the tool calls among the results of the process; a turn that
   *     waits for results of the process answers nothing
   */
  CompositionResult compose(
      AgentConfiguration configuration,
      AgentContext agentContext,
      PreviousConversation previousConversation,
      AgentInput agentInput,
      RuntimeToolCallSplit deferredCalls);

  /**
   * Composes the message that carries the content of the documents the answers of the agent itself
   * name (see {@code RuntimeToolResult#withDocumentContent}). The message shows the tag of each
   * document with content, so the content of the document is added for the model. It is evicted
   * together with the tool call results (see {@link UserMessage#METADATA_TOOL_CALL_DOCUMENTS}).
   *
   * @param registry the documents the agent knows
   * @param answers the answers of the agent itself to the tool calls of one turn input
   * @return the message, or {@code null} if no answer names a document known to the registry
   */
  @Nullable UserMessage composeDocumentContentMessage(
      DocumentRegistry registry, List<RuntimeToolCallAnswer> answers);
}
