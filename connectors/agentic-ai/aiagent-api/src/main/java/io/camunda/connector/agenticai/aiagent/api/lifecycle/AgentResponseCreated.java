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
 * The turns of the job are done, the agent response is created, and the agent context is about to
 * be persisted. An empty {@code toolCalls} list means the response is the final answer.
 *
 * @param iterationKey the iteration key of the last turn of the job
 * @param toolCalls the tool calls of the last turn; the agent answers its local calls in the job
 *     that consumes the results of the process calls
 * @param assistantMessage the response of the model in the last turn
 * @param jobMetrics the metrics of all turns of the job
 */
@Experimental
public record AgentResponseCreated(
    int iterationKey,
    List<ToolCall> toolCalls,
    @Nullable AssistantMessage assistantMessage,
    AgentMetrics jobMetrics) {

  public boolean finalAnswer() {
    return toolCalls.isEmpty();
  }
}
