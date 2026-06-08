/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.model.message.Message;
import java.util.List;

/** Decision produced by AgentInputComposer: whether and how to proceed with the next LLM call. */
public sealed interface AgentInput permits AgentInput.NoOp, AgentInput.Cancel, AgentInput.Proceed {

  /** No messages ready yet — wait for more tool results before proceeding. */
  record NoOp() implements AgentInput {}

  /** Conversation cannot continue — e.g., no user message content available. */
  record Cancel(String errorCode, String message) implements AgentInput {}

  /** Messages assembled and ready to be applied to the conversation. */
  record Proceed(List<Message> messages) implements AgentInput {
    public Proceed {
      messages = List.copyOf(messages);
    }
  }
}
