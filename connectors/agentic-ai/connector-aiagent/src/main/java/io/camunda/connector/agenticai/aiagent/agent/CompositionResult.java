/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import java.util.List;
import org.jspecify.annotations.Nullable;

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
   * results: the local tool calls. The results of the process calls are in the batch.
   *
   * @param toolCallBatch the calls of the previous turn, none when the next turn has no tool call
   *     results
   * @param userPrompt the user message of a turn without tool call results, if there is any
   * @param eventMessages the messages of the events, added after the other input
   */
  record Ready(
      ToolCallBatch toolCallBatch, @Nullable UserMessage userPrompt, List<Message> eventMessages)
      implements CompositionResult {

    public Ready {
      if (userPrompt != null && !toolCallBatch.toolCalls().isEmpty()) {
        throw new IllegalArgumentException(
            "The next turn has either tool call results or a user prompt, not both");
      }
      eventMessages = List.copyOf(eventMessages);
    }

    /** The next turn that answers the calls of the batch only. */
    public static Ready of(ToolCallBatch toolCallBatch) {
      return new Ready(toolCallBatch, null, List.of());
    }

    public List<ToolCall> unansweredToolCalls() {
      return toolCallBatch.unanswered();
    }

    public Ready withResults(List<ToolCallBatch.Entry> entries) {
      return new Ready(toolCallBatch.withEntries(entries), userPrompt, eventMessages);
    }
  }
}
