/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.internaltool;

import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registry of all registered {@link InternalToolHandler}s. T3 (sub-loop) uses the classification
 * methods to decide whether a batch of tool calls can be executed in-process or must be dispatched
 * via the AHSP.
 */
public class InternalToolRegistry {

  private final Map<String, InternalToolHandler> handlers;

  public InternalToolRegistry(List<InternalToolHandler> handlers) {
    this.handlers = new LinkedHashMap<>();
    for (InternalToolHandler h : handlers) {
      this.handlers.put(h.name(), h);
    }
  }

  /** Returns tool definitions for all registered handlers, in registration order. */
  public List<ToolDefinition> toolDefinitions() {
    return handlers.values().stream()
        .map(InternalToolHandler::definition)
        .collect(Collectors.toList());
  }

  /** Returns the set of all registered internal tool names. */
  public Set<String> toolNames() {
    return Set.copyOf(handlers.keySet());
  }

  /** Returns {@code true} if {@code name} is a registered internal tool. */
  public boolean isInternalTool(String name) {
    return handlers.containsKey(name);
  }

  /** Returns {@code true} if the tool call targets a registered internal tool. */
  public boolean isInternalToolCall(ToolCall toolCall) {
    return isInternalTool(toolCall.name());
  }

  /**
   * Finds the handler for the given name, or {@code null} if not registered. Package-private — used
   * by {@link InternalToolExecutor}.
   */
  InternalToolHandler findHandler(String name) {
    return handlers.get(name);
  }
}
