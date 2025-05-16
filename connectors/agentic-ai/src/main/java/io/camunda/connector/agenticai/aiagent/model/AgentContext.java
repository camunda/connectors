/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse.AdHocToolDefinition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AgentContext(
    AgentState state,
    AgentMetrics metrics,
    List<Map<String, Object>> memory,
    List<AdHocToolDefinition> toolDefinitions,
    Map<String, Object> properties) {

  public static final AgentContext EMPTY =
      new AgentContext(
          AgentState.EMPTY,
          AgentMetrics.EMPTY,
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyMap());

  public AgentContext {
    Objects.requireNonNull(state, "Agent state must not be null");
    Objects.requireNonNull(metrics, "Agent metrics must not be null");
    Objects.requireNonNull(memory, "Agent memory must not be null");
    Objects.requireNonNull(toolDefinitions, "Tool specifications must not be null");
    Objects.requireNonNull(properties, "Properties must not be null");

    memory = Collections.unmodifiableList(memory);
    toolDefinitions = Collections.unmodifiableList(toolDefinitions);
    properties = Collections.unmodifiableMap(properties);
  }

  public AgentContext withState(AgentState state) {
    return new AgentContext(state, metrics, memory, toolDefinitions, properties);
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
    return new AgentContext(state, metrics, memory, toolDefinitions, properties);
  }

  public AgentContext withMemory(List<Map<String, Object>> memory) {
    return new AgentContext(state, metrics, memory, toolDefinitions, properties);
  }

  public AgentContext withToolDefinitions(List<AdHocToolDefinition> toolDefinitions) {
    return new AgentContext(state, metrics, memory, toolDefinitions, properties);
  }

  public AgentContext withProperties(Map<String, Object> properties) {
    return new AgentContext(state, metrics, memory, toolDefinitions, properties);
  }

  public AgentContext withProperty(String key, Object value) {
    final var properties = new LinkedHashMap<>(this.properties);
    properties.put(key, value);
    return withProperties(properties);
  }
}
