/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import java.time.Duration;

public interface AiFrameworkChatResponse<T> {
  AssistantMessage assistantMessage();

  /**
   * The metrics of the chat interaction (model calls, tool calls, token usage). Execution duration
   * is only provided when a timed request is being executed. See {@link
   * AiFrameworkAdapter#executeMeasuringTime}.
   */
  AgentMetrics metrics();

  /**
   * Returns a copy of this response whose {@link #metrics()} carry the given execution duration.
   * Used by {@link AiFrameworkAdapter#executeMeasuringTime} once the request has been timed.
   */
  AiFrameworkChatResponse<T> withExecutionTimeMetrics(Duration executionTime);

  T rawChatResponse();
}
