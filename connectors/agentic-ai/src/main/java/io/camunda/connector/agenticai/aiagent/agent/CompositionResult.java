/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.model.message.Message;
import java.util.List;

/**
 * Decision produced by ConversationTurnComposer: whether and how to proceed with the next LLM call.
 */
public sealed interface CompositionResult {

  /** No messages ready yet — wait for more tool results before proceeding. */
  record Deferred() implements CompositionResult {}

  /**
   * No input could be composed for this turn (no user prompt, documents or events to add). Carries
   * no error semantics — each handler decides whether this is a hard error or a benign wait.
   */
  record NoInput() implements CompositionResult {}

  /** Messages assembled and ready to be applied to the conversation. */
  record NextTurn(List<Message> messages) implements CompositionResult {
    public NextTurn {
      messages = List.copyOf(messages);
    }
  }
}
