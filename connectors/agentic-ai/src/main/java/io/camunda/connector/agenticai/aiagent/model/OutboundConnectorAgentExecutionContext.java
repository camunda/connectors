/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ToolsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class OutboundConnectorAgentExecutionContext implements AgentExecutionContext {

  private final OutboundConnectorAgentJobContext jobContext;
  private final AgentRequest request;
  private final ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
  private List<AdHocToolElement> toolElements;

  public OutboundConnectorAgentExecutionContext(
      OutboundConnectorAgentJobContext jobContext,
      AgentRequest request,
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver) {
    this.jobContext = jobContext;
    this.request = request;
    this.toolElementsResolver = toolElementsResolver;
  }

  @Override
  public AgentJobContext jobContext() {
    return jobContext;
  }

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
  public List<AdHocToolElement> toolElements() {
    if (toolElements != null) {
      return toolElements;
    }

    return toolElements = resolveToolElements();
  }

  private List<AdHocToolElement> resolveToolElements() {
    final var toolsContainerElementId =
        Optional.ofNullable(request.data().tools())
            .map(ToolsConfiguration::containerElementId)
            .filter(id -> !id.isBlank())
            .orElse(null);

    if (toolsContainerElementId == null) {
      return Collections.emptyList();
    }

    return toolElementsResolver.resolveToolElements(
        jobContext.processDefinitionKey(), toolsContainerElementId);
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
