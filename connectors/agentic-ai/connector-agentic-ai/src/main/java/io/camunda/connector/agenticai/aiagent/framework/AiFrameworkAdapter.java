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

public interface AiFrameworkAdapter<R extends AiFrameworkChatResponse<?>> {

  /**
   * Executes the chat request and returns the response with its {@link
   * AiFrameworkChatResponse#metrics() metrics} enriched by the measured execution duration.
   */
  @SuppressWarnings("unchecked")
  default R executeMeasuringTime(
      AgentExecutionContext executionContext, ConversationSnapshot snapshot) {
    // monotonic clock: wall-clock (Instant.now) can jump backwards/forwards on NTP or VM clock
    // adjustments, yielding negative or skewed durations
    final var startNanos = System.nanoTime();
    final var response = executeChatRequest(executionContext, snapshot);
    final var executionTime = Duration.ofNanos(System.nanoTime() - startNanos);

    return (R) response.withExecutionTimeMetrics(executionTime);
  }

  /**
   * Executes the chat request using the underlying chat framework. The returned response carries
   * the {@link AiFrameworkChatResponse#metrics() metrics} of the chat interaction (model calls,
   * tool calls, token usage).
   */
  R executeChatRequest(AgentExecutionContext executionContext, ConversationSnapshot snapshot);
}
