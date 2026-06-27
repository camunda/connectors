/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.common.AgenticAiRecord;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@AgenticAiRecord
public record AgentInstanceUpdateRequest(
    @Nullable AgentInstanceUpdateStatus status,
    @Nullable AgentMetrics delta,
    @Nullable List<ToolDefinition> tools)
    implements AgentInstanceUpdateRequestBuilder.With {

  public static AgentInstanceUpdateRequest statusOnly(AgentInstanceUpdateStatus status) {
    Objects.requireNonNull(status, "status must not be null");
    return builder().status(status).build();
  }

  public static AgentInstanceUpdateRequestBuilder builder() {
    return AgentInstanceUpdateRequestBuilder.builder();
  }
}
