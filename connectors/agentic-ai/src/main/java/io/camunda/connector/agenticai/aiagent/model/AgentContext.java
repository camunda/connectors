/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse.AdHocToolDefinition;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AgentContext(
    AgentState state,
    AgentMetrics metrics,
    List<Map<String, Object>> memory,
    List<AdHocToolDefinition> toolDefinitions,
    List<String> mcpClientIds) {

  public static final AgentContext EMPTY =
      new AgentContext(
          AgentState.EMPTY,
          AgentMetrics.EMPTY,
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList());

  public AgentContext {
    Objects.requireNonNull(state, "Agent state must not be null");
    Objects.requireNonNull(metrics, "Agent metrics must not be null");
    Objects.requireNonNull(memory, "Agent memory must not be null");
    Objects.requireNonNull(toolDefinitions, "Tool specifications must not be null");
    Objects.requireNonNull(mcpClientIds, "MCP client IDs must not be null");
  }

  public AgentContext withState(AgentState state) {
    return new AgentContext(state, metrics, memory, toolDefinitions, mcpClientIds);
  }

  public boolean isInState(AgentState state) {
    return this.state == state;
  }

  public boolean isEmpty() {
    return this.equals(EMPTY);
  }

  public static AgentContext empty() {
    return EMPTY;
  }

  public AgentContext withMetrics(AgentMetrics metrics) {
    return new AgentContext(state, metrics, memory, toolDefinitions, mcpClientIds);
  }

  public AgentContext withMemory(List<Map<String, Object>> memory) {
    return new AgentContext(state, metrics, Collections.unmodifiableList(memory), toolDefinitions, mcpClientIds);
  }

  public AgentContext withToolDefinitions(List<AdHocToolDefinition> toolDefinitions) {
    return new AgentContext(state, metrics, memory, Collections.unmodifiableList(toolDefinitions), mcpClientIds);
  }

  public AgentContext withMcpClientIds(List<String> mcpClientIds) {
    return new AgentContext(
        state, metrics, memory, toolDefinitions, Collections.unmodifiableList(mcpClientIds));
  }
}
