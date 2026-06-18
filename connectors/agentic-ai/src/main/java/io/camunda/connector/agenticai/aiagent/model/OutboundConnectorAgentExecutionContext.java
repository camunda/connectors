/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.aiagent.model.request.OutboundConnectorAgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ToolsConfiguration;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.outbound.JobContext;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class OutboundConnectorAgentExecutionContext implements AgentExecutionContext {

  private final JobContext jobContext;
  private final OutboundConnectorAgentRequest request;
  private final ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
  private List<AdHocToolElement> toolElements;
  private AgentConfiguration configuration;

  public OutboundConnectorAgentExecutionContext(
      JobContext jobContext,
      OutboundConnectorAgentRequest request,
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver) {
    this.jobContext = jobContext;
    this.request = request;
    this.toolElementsResolver = toolElementsResolver;
  }

  @Override
  public JobContext jobContext() {
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
        jobContext.getProcessDefinitionKey(), toolsContainerElementId);
  }

  @Override
  public UserPromptConfiguration userPrompt() {
    return request.data().userPrompt();
  }

  public ToolsConfiguration tools() {
    return request.data().tools();
  }

  @Override
  public AgentConfiguration configuration() {
    if (configuration == null) {
      configuration =
          new AgentConfiguration(
              request.provider(),
              request.data().systemPrompt(),
              request.data().memory(),
              request.data().limits(),
              // the outbound connector flavor does not support event handling
              null,
              request.data().response());
    }
    return configuration;
  }
}
