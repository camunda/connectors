/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.tools.schema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.api.outbound.JobContext;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class AgentTaskExecutionContext implements AgentExecutionContext {

  private final JobContext jobContext;
  private final AgentContext initialAgentContext;
  private final List<ToolCallResult> initialToolCallResults;
  @Nullable private final String toolsContainerElementId;
  private final ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
  private final AgentConfiguration configuration;

  @Nullable private List<AdHocToolElement> toolElements;

  public AgentTaskExecutionContext(
      JobContext jobContext,
      AgentConfiguration configuration,
      AgentContext initialAgentContext,
      List<ToolCallResult> initialToolCallResults,
      @Nullable String toolsContainerElementId,
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver) {
    this.jobContext = jobContext;
    this.configuration = configuration;
    this.initialAgentContext = initialAgentContext;
    this.initialToolCallResults = initialToolCallResults;
    this.toolsContainerElementId = toolsContainerElementId;
    this.toolElementsResolver = toolElementsResolver;
  }

  @Override
  public JobContext jobContext() {
    return jobContext;
  }

  @Override
  public AgentContext initialAgentContext() {
    return initialAgentContext;
  }

  @Override
  public List<ToolCallResult> initialToolCallResults() {
    return initialToolCallResults;
  }

  @Override
  public List<AdHocToolElement> toolElements() {
    if (toolElements != null) {
      return toolElements;
    }

    return toolElements = resolveToolElements();
  }

  private List<AdHocToolElement> resolveToolElements() {
    if (toolsContainerElementId == null || toolsContainerElementId.isBlank()) {
      return Collections.emptyList();
    }

    return toolElementsResolver.resolveToolElements(
        jobContext.getPhysicalTenantId(),
        jobContext.getProcessDefinitionKey(),
        toolsContainerElementId);
  }

  @Override
  public UserPromptConfiguration userPrompt() {
    return configuration.userPrompt();
  }

  @Override
  public AgentConfiguration configuration() {
    return configuration;
  }
}
