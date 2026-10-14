/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Decision produced by AgentConversationTurnInputComposer: whether and how to proceed with the next
 * LLM call.
 */
public sealed interface CompositionResult {

  /**
   * No messages ready yet — wait for more tool results before proceeding.
   *
   * @param arrivedResults the tool call results that have arrived from the process so far for the
   *     pending turn, already correlated to a tool call this turn is waiting on and gateway
   *     (MCP/A2A) transformed; the answers of the agent itself are not included
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
   * Messages assembled and ready to be applied to the conversation.
   *
   * @param documents the documents that entered the conversation with these messages (user prompt,
   *     tool call results and events), to be registered in the document registry
   * @param contextUpdates the changes of the agent context the tool calls answered by the agent
   *     made while the tool call results were composed, in call order
   */
  record NextTurn(
      List<Message> messages,
      List<IngestedDocument> documents,
      List<UnaryOperator<AgentContext>> contextUpdates)
      implements CompositionResult {
    public NextTurn {
      messages = List.copyOf(messages);
      documents = List.copyOf(documents);
      contextUpdates = List.copyOf(contextUpdates);
    }

    public NextTurn(List<Message> messages, List<IngestedDocument> documents) {
      this(messages, documents, List.of());
    }

    public NextTurn(List<Message> messages) {
      this(messages, List.of());
    }
  }
}
