/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.OutboundConnectorAgentRequest.OutboundConnectorAgentRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ToolsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.api.outbound.JobContext;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Version-agnostic execution context for the AI Agent Task flavor (serves v1 and v2). */
public class OutboundConnectorAgentExecutionContext implements AgentExecutionContext {

  private final JobContext jobContext;
  private final OutboundConnectorAgentRequestData data;
  private final ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
  private final AgentConfiguration configuration;

  @Nullable private List<AdHocToolElement> toolElements;

  public OutboundConnectorAgentExecutionContext(
      JobContext jobContext,
      OutboundConnectorAgentRequestData data,
      ChatModelApiConfiguration chatModelApiConfiguration,
      String modelName,
      String modelProvider,
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver) {
    this.jobContext = jobContext;
    this.data = data;
    this.toolElementsResolver = toolElementsResolver;
    this.configuration =
        new AgentConfiguration(
            chatModelApiConfiguration,
            modelName,
            modelProvider,
            data.systemPrompt(),
            data.userPrompt(),
            data.memory(),
            data.limits(),
            // the outbound connector flavor does not support event handling
            null,
            data.response());
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
        jobContext.getProcessDefinitionKey(), toolsContainerElementId);
  }

  @Override
  public UserPromptConfiguration userPrompt() {
    return data.userPrompt();
  }

  public @Nullable ToolsConfiguration tools() {
    return data.tools();
  }

  @Override
  public AgentConfiguration configuration() {
    return configuration;
  }
}
