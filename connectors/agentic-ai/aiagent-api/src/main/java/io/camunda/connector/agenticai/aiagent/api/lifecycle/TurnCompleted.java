/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.lifecycle;

import io.camunda.connector.agenticai.aiagent.api.Experimental;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The turn's model rounds are done and the agent context is about to be persisted. An empty {@code
 * toolCalls} list means the turn produced the final answer.
 *
 * @param iterationKey the iteration key of the last model round of the job; a provider continuation
 *     creates a further conversation turn with the next key, so it differs from {@link
 *     TurnStarted#iterationKey()} when the job ran more than one round
 */
@Experimental
public record TurnCompleted(
    int iterationKey,
    List<ToolCall> toolCalls,
    @Nullable AssistantMessage assistantMessage,
    AgentMetrics turnMetrics) {

  public boolean finalAnswer() {
    return toolCalls.isEmpty();
  }
}
