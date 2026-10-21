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
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskRequestData;
import io.camunda.connector.agenticai.aiagent.request.ToolsConfiguration;
import io.camunda.connector.agenticai.aiagent.translation.AgentConfigurationTranslator;
import io.camunda.connector.agenticai.tools.schema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.api.outbound.JobContext;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public class AgentTaskExecutionContext implements AgentExecutionContext {

  private final JobContext jobContext;
  private final AgentTaskRequestData data;
  private final ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
  private final AgentConfiguration configuration;

  @Nullable private List<AdHocToolElement> toolElements;

  public AgentTaskExecutionContext(
      JobContext jobContext,
      AgentTaskRequestData data,
      ChatModelConfiguration chatModel,
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver) {
    this.jobContext = jobContext;
    this.data = data;
    this.toolElementsResolver = toolElementsResolver;
    this.configuration = AgentConfigurationTranslator.translate(chatModel, data);
  }

  @Override
  public JobContext jobContext() {
    return jobContext;
  }

  @Override
  public AgentContext initialAgentContext() {
    return data.context();
  }

  @Override
  public List<ToolCallResult> initialToolCallResults() {
    return Optional.ofNullable(data.tools())
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
        Optional.ofNullable(data.tools())
            .map(ToolsConfiguration::containerElementId)
            .filter(id -> !id.isBlank())
            .orElse(null);

    if (toolsContainerElementId == null) {
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

  public @Nullable ToolsConfiguration tools() {
    return data.tools();
  }

  @Override
  public AgentConfiguration configuration() {
    return configuration;
  }
}
