/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Matches incoming tool call results against expected tool calls from the last assistant message.
 * Produces a {@link ToolCallResultsPartition} with ordered results and any missing tool calls.
 *
 * <p>When {@code interruptMissing} is true, missing tool calls are added as synthetic cancelled
 * results so the LLM can see that they were interrupted.
 */
public final class ToolCallResultsPartitioner {

  private static final Logger LOGGER = LoggerFactory.getLogger(ToolCallResultsPartitioner.class);

  private ToolCallResultsPartitioner() {}

  public static ToolCallResultsPartition partition(
      List<ToolCall> expectedToolCalls,
      List<ToolCallResult> transformedResults,
      boolean interruptMissing) {
    final var resultsById =
        transformedResults.stream()
            .collect(Collectors.toMap(ToolCallResult::id, Function.identity()));

    final var missingToolCalls = new ArrayList<ToolCall>();
    final var orderedResults = new ArrayList<ToolCallResult>();

    expectedToolCalls.forEach(
        toolCall -> {
          final var result = resultsById.get(toolCall.id());
          if (result != null) {
            orderedResults.add(result);
          } else {
            missingToolCalls.add(toolCall);
            if (interruptMissing) {
              LOGGER.debug(
                  "Missing tool call result for ID: {}. Marking as canceled.", toolCall.id());
              orderedResults.add(
                  ToolCallResult.forCancelledToolCall(toolCall.id(), toolCall.name()));
            }
          }
        });

    return new ToolCallResultsPartition(orderedResults, missingToolCalls);
  }
}
