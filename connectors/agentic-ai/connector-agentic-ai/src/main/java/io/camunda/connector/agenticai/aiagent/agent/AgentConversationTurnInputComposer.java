/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentInput;
import io.camunda.connector.agenticai.aiagent.model.PreviousConversation;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Composes the next conversation turn input from raw history and the current invocation state. */
public interface AgentConversationTurnInputComposer {
  CompositionResult compose(
      AgentConfiguration configuration,
      AgentContext agentContext,
      PreviousConversation previousConversation,
      AgentInput agentInput);

  /**
   * Composes the message that carries the content of the documents the given tool call results get
   * into the conversation: the results of {@code c8__get_document}. The message shows the tag of
   * each document with content, so the content of the document is added for the model. It is
   * evicted together with the tool call results (see {@link
   * UserMessage#METADATA_TOOL_CALL_DOCUMENTS}).
   *
   * @param registry the documents the agent knows
   * @param results the tool call results of one turn input, including the ones answered by the
   *     agent
   * @return the message, or {@code null} if no result loads a document known to the registry
   */
  @Nullable UserMessage composeDocumentContentMessage(
      DocumentRegistry registry, List<ToolCallResult> results);
}
