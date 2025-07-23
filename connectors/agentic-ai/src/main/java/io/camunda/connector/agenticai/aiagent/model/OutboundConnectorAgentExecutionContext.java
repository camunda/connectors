/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.ToolsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public record OutboundConnectorAgentExecutionContext(
    OutboundConnectorAgentJobContext jobContext, AgentRequest request)
    implements AgentExecutionContext {

  @Override
  public AgentContext initialAgentContext() {
    return request.data().context();
  }

  @Override
  public List<ToolCallResult> initialToolCallResults() {
    return Optional.ofNullable(request.data().tools())
        .map(ToolsConfiguration::toolCallResults)
        .orElseGet(Collections::emptyList);
  }

  @Override
  public ProviderConfiguration provider() {
    return request.provider();
  }

  @Override
  public SystemPromptConfiguration systemPrompt() {
    return request.data().systemPrompt();
  }

  @Override
  public UserPromptConfiguration userPrompt() {
    return request.data().userPrompt();
  }

  public ToolsConfiguration tools() {
    return request.data().tools();
  }

  @Override
  public MemoryConfiguration memory() {
    return request.data().memory();
  }

  @Override
  public LimitsConfiguration limits() {
    return request.data().limits();
  }

  @Override
  public ResponseConfiguration response() {
    return request.data().response();
  }
}
