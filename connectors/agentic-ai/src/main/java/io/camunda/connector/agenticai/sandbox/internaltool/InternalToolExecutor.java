/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.internaltool;

import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dispatches a list of tool calls to their respective {@link InternalToolHandler}s. All errors are
 * caught and returned as result content — no exceptions propagate out of this class. Results are
 * tagged with {@code executedBy=sandbox} in {@link ToolCallResult#properties()} so a UI can
 * distinguish in-process execution from BPMN-dispatched execution (design §8).
 */
public class InternalToolExecutor {

  static final String PROPERTY_EXECUTED_BY = "executedBy";
  static final String EXECUTED_BY_SANDBOX = "sandbox";

  private final InternalToolRegistry registry;

  public InternalToolExecutor(InternalToolRegistry registry) {
    this.registry = registry;
  }

  /**
   * Executes each call in {@code toolCalls} against {@code session} with the given per-invocation
   * {@code context}. The returned list has one entry per input call, in the same order.
   */
  public List<ToolCallResult> execute(
      List<ToolCall> toolCalls, SandboxSession session, InternalToolContext context) {
    List<ToolCallResult> results = new ArrayList<>(toolCalls.size());
    for (ToolCall call : toolCalls) {
      results.add(executeSingle(call, session, context));
    }
    return results;
  }

  private ToolCallResult executeSingle(
      ToolCall call, SandboxSession session, InternalToolContext context) {
    InternalToolHandler handler = registry.findHandler(call.name());
    if (handler == null) {
      return errorResult(call, "Unknown internal tool: " + call.name());
    }
    try {
      ToolCallResult raw = handler.execute(call, session, context);
      // Ensure executedBy tag is always present, even if handler set its own properties.
      if (!EXECUTED_BY_SANDBOX.equals(raw.properties().get(PROPERTY_EXECUTED_BY))) {
        Map<String, Object> props = new java.util.HashMap<>(raw.properties());
        props.put(PROPERTY_EXECUTED_BY, EXECUTED_BY_SANDBOX);
        return ToolCallResult.builder()
            .id(raw.id())
            .name(raw.name())
            .content(raw.content())
            .properties(props)
            .build();
      }
      return raw;
    } catch (Exception e) {
      return errorResult(
          call, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
    }
  }

  private static ToolCallResult errorResult(ToolCall call, String message) {
    return ToolCallResult.builder()
        .id(call.id())
        .name(call.name())
        .content("Error: " + message)
        .properties(Map.of(PROPERTY_EXECUTED_BY, EXECUTED_BY_SANDBOX))
        .build();
  }
}
