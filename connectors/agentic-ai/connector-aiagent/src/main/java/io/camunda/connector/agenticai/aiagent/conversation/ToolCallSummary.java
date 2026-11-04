/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.conversation;

import java.util.Map;
import java.util.Optional;

/** Tool call counts of one completed turn. */
public record ToolCallSummary(
    int iterationKey,
    int toolCallCount,
    Map<String, Long> callsPerTool,
    Optional<String> firstToolName,
    String responseKind) {

  public ToolCallSummary {
    callsPerTool = Map.copyOf(callsPerTool);
  }
}
