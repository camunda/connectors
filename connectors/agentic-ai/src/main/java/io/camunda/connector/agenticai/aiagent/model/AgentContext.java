/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.agenticai.aiagent.memory.MemoryData;
import io.camunda.connector.agenticai.aiagent.memory.ProcessVariableMemoryData;
import io.camunda.connector.agenticai.model.AgenticAiRecordBuilder;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@AgenticAiRecordBuilder
public record AgentContext(
    AgentState state, AgentMetrics metrics, List<ToolDefinition> toolDefinitions, MemoryData memory)
    implements AgentContextBuilder.With {

  public AgentContext {
    Objects.requireNonNull(state, "Agent state must not be null");
    Objects.requireNonNull(metrics, "Agent metrics must not be null");
    Objects.requireNonNull(toolDefinitions, "Tool definitions must not be null");
    Objects.requireNonNull(memory, "Agent memory must not be null");
  }

  public static final AgentContext EMPTY =
      new AgentContext(
          AgentState.READY,
          AgentMetrics.EMPTY,
          Collections.emptyList(),
          new ProcessVariableMemoryData(Collections.emptyList()));

  public boolean isInState(AgentState state) {
    return this.state == state;
  }

  @JsonIgnore
  public boolean isEmpty() {
    return this.equals(EMPTY);
  }

  public static AgentContext empty() {
    return EMPTY;
  }
}
