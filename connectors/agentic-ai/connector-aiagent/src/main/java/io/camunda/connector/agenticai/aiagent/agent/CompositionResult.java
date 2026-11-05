/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import java.util.List;
import java.util.Set;

/** Whether the next turn can be composed, as resolved by AgentConversationTurnInputComposer. */
public sealed interface CompositionResult {

  /**
   * No messages ready yet — wait for more tool results before proceeding.
   *
   * @param arrivedResults the tool call results that have arrived from the process so far for the
   *     pending turn, already correlated to a tool call this turn is waiting on and gateway
   *     (MCP/A2A) transformed
   */
  record Deferred(List<ToolCallResult> arrivedResults) implements CompositionResult {
    public Deferred {
      arrivedResults = List.copyOf(arrivedResults);
    }
  }

  /**
   * No input could be composed for this turn (no user prompt, documents or events to add). Carries
   * no error semantics — each handler decides whether this is a hard error or a benign wait.
   */
  record NoInput() implements CompositionResult {}

  /**
   * The next turn can be assembled once the calls of the batch that are still unanswered have their
   * results: the calls of runtime tools. The results of the process calls and of the rejected calls
   * are in the batch, and so are the cancelled results of an interrupted turn.
   *
   * @param agentContext the agent context the next turn is composed with
   * @param documentIdsWithContent the ids of the documents whose content the model reads as of the
   *     start of the batch
   * @param toolCallBatch the calls of the previous turn, none when the next turn has no tool call
   *     results
   * @param userPrompt the input of the user prompt of a turn without tool call results
   * @param events the input of the events, added after the other input
   */
  record Ready(
      AgentContext agentContext,
      Set<AgentDocumentId> documentIdsWithContent,
      ToolCallBatch toolCallBatch,
      TurnInput userPrompt,
      TurnInput events)
      implements CompositionResult {

    public Ready {
      if (!userPrompt.isEmpty() && !toolCallBatch.toolCalls().isEmpty()) {
        throw new IllegalArgumentException(
            "The next turn has either tool call results or a user prompt, not both");
      }
      documentIdsWithContent = Set.copyOf(documentIdsWithContent);
    }

    /** The next turn that answers the calls of the batch only. */
    public static Ready of(
        AgentContext agentContext,
        Set<AgentDocumentId> documentIdsWithContent,
        ToolCallBatch toolCallBatch) {
      return new Ready(
          agentContext,
          documentIdsWithContent,
          toolCallBatch,
          TurnInput.empty(),
          TurnInput.empty());
    }

    public List<ToolCall> unansweredToolCalls() {
      return toolCallBatch.unanswered();
    }

    public Ready withResults(List<ToolCallBatch.Entry> entries) {
      return new Ready(
          agentContext,
          documentIdsWithContent,
          toolCallBatch.withEntries(entries),
          userPrompt,
          events);
    }
  }
}
