/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;

/**
 * Result of partitioning incoming tool call results against the expected tool calls from the last
 * assistant message.
 *
 * @param processedResults tool call results matched by ID to expected tool calls, in order
 * @param missingToolCalls expected tool calls with no matching result yet
 */
public record ToolCallResultsPartition(
    List<ToolCallResult> processedResults, List<ToolCall> missingToolCalls) {

  public boolean isComplete() {
    return missingToolCalls.isEmpty();
  }
}
