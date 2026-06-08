/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import java.util.List;
import java.util.Objects;

/**
 * One completed LLM invocation: the messages that drove it, the response, and per-turn metrics.
 * iterationKey is 1-based and counts LLM calls across the entire agent lifecycle.
 */
public record ConversationTurn(
    int iterationKey,
    List<Message> inputMessages,
    AssistantMessage assistantMessage,
    AgentMetrics metrics) {

  /** Metadata key used to store iterationKey on persisted messages. */
  public static final String METADATA_ITERATION_KEY = "iterationKey";

  public ConversationTurn {
    Objects.requireNonNull(inputMessages, "inputMessages must not be null");
    Objects.requireNonNull(assistantMessage, "assistantMessage must not be null");
    Objects.requireNonNull(metrics, "metrics must not be null");
    inputMessages = List.copyOf(inputMessages);
  }

  public boolean hasToolCalls() {
    return assistantMessage.hasToolCalls();
  }
}
