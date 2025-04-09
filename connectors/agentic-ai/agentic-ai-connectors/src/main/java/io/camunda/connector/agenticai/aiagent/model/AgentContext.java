/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record AgentContext(
    AgentState state, AgentMetrics metrics, List<Map<String, Object>> memory) {
  public AgentContext withState(AgentState state) {
    return new AgentContext(state, metrics, memory);
  }

  public boolean isInState(AgentState state) {
    return this.state == state;
  }

  public AgentContext withMetrics(AgentMetrics metrics) {
    return new AgentContext(state, metrics, memory);
  }

  public AgentContext withMemory(List<Map<String, Object>> memory) {
    return new AgentContext(state, metrics, memory);
  }

  public static AgentContext empty() {
    return new AgentContext(AgentState.READY, AgentMetrics.empty(), Collections.emptyList());
  }
}
