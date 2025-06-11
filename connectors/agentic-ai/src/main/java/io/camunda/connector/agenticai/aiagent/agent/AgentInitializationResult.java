/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;
import org.springframework.lang.Nullable;

@AgenticAiRecord
public record AgentInitializationResult(
    @Nullable AgentContext agentContext,
    List<ToolCallResult> toolCallResults,
    @Nullable AgentResponse agentResponse)
    implements AgentInitializationResultBuilder.With {

  public static AgentInitializationResultBuilder builder() {
    return AgentInitializationResultBuilder.builder();
  }
}
