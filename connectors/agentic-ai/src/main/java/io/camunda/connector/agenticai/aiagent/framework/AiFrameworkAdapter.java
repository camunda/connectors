/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework;

import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import java.time.Duration;
import java.time.Instant;

public interface AiFrameworkAdapter<R extends AiFrameworkChatResponse<?>> {

  /**
   * Executes the chat request and returns the response with its {@link
   * AiFrameworkChatResponse#metrics() metrics} enriched by the measured execution duration.
   */
  @SuppressWarnings("unchecked")
  default R executeMeasuringTime(
      AgentExecutionContext executionContext, ConversationSnapshot snapshot) {
    final var startTime = Instant.now();
    final var response = executeChatRequest(executionContext, snapshot);
    final var executionTime = Duration.between(startTime, Instant.now());

    return (R) response.withExecutionTimeMetrics(executionTime);
  }

  /**
   * Executes the chat request using the underlying chat framework. The returned response carries
   * the {@link AiFrameworkChatResponse#metrics() metrics} of the chat interaction (model calls,
   * tool calls, token usage).
   */
  R executeChatRequest(AgentExecutionContext executionContext, ConversationSnapshot snapshot);
}
