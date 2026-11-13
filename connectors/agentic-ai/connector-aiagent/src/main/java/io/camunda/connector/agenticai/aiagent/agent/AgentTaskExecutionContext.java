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
import io.camunda.connector.agenticai.aiagent.request.AgentTaskV1Request;
import io.camunda.connector.agenticai.aiagent.request.ToolsConfiguration;
import io.camunda.connector.agenticai.tools.schema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.outbound.JobContext;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public class AgentTaskExecutionContext implements AgentExecutionContext {

  private final JobContext jobContext;
  private final DocumentFactory documentFactory;
  private final AgentTaskRequestData data;
  private final ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
  private final AgentConfiguration configuration;
  private final List<String> newInstanceFeatures;

  @Nullable private List<AdHocToolElement> toolElements;

  public AgentTaskExecutionContext(
      JobContext jobContext,
      DocumentFactory documentFactory,
      AgentTaskV1Request request,
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver,
      List<String> newInstanceFeatures) {
    this(
        jobContext,
        documentFactory,
        request.data(),
        request.provider(),
        toolElementsResolver,
        newInstanceFeatures);
  }

  public AgentTaskExecutionContext(
      JobContext jobContext,
      DocumentFactory documentFactory,
      AgentTaskRequestData data,
      ChatModelConfiguration chatModel,
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver,
      List<String> newInstanceFeatures) {
    this.jobContext = jobContext;
    this.documentFactory = documentFactory;
    this.data = data;
    this.toolElementsResolver = toolElementsResolver;
    this.newInstanceFeatures = newInstanceFeatures;
    this.configuration =
        new AgentConfiguration(
            chatModel,
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
  public DocumentFactory documentFactory() {
    return documentFactory;
  }

  @Override
  public List<String> newInstanceFeatures() {
    return newInstanceFeatures;
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

    toolElements = resolveToolElements();
    return toolElements;
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
