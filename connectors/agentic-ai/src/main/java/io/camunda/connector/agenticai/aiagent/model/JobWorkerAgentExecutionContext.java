/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElementParameter;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerAgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.outbound.JobContext;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobWorkerAgentExecutionContext implements AgentExecutionContext {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(JobWorkerAgentExecutionContext.class);

  private final JobContext jobContext;
  private final JobWorkerAgentRequest request;
  private final ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
  private boolean cancelRemainingInstances;
  private List<AdHocToolElement> toolElements;

  public JobWorkerAgentExecutionContext(
      final JobContext jobContext,
      final JobWorkerAgentRequest request,
      final ProcessDefinitionAdHocToolElementsResolver toolElementsResolver) {
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
    return request.agentContext();
  }

  @Override
  public List<ToolCallResult> initialToolCallResults() {
    return request.toolCallResults();
  }

  @Override
  public List<AdHocToolElement> toolElements() {
    if (toolElements != null) {
      return toolElements;
    }

    return toolElements = enrichWithParameters(request.toolElements());
  }

  /**
   * Zeebe's adHocSubProcessElements variable provides element metadata (id, name, documentation,
   * zeebe:properties) but not tool parameters from specext:externalParameters or fromAi()
   * expressions. This method enriches each element with parameters resolved from the BPMN XML.
   */
  private List<AdHocToolElement> enrichWithParameters(List<AdHocToolElement> zeebeElements) {
    if (zeebeElements == null || zeebeElements.isEmpty() || toolElementsResolver == null) {
      return zeebeElements;
    }

    try {
      final Map<String, List<AdHocToolElementParameter>> paramsByElementId =
          toolElementsResolver
              .resolveToolElements(jobContext.getProcessDefinitionKey(), jobContext.getElementId())
              .stream()
              .collect(Collectors.toMap(AdHocToolElement::elementId, AdHocToolElement::parameters));

      return zeebeElements.stream()
          .map(
              element ->
                  element.withParameters(
                      paramsByElementId.getOrDefault(element.elementId(), List.of())))
          .toList();
    } catch (Exception e) {
      LOGGER.warn(
          "Failed to enrich tool elements with parameters from process definition. "
              + "Tool parameters will not be available for schema generation. Cause: {}",
          e.getMessage());
      return zeebeElements;
    }
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

  @Override
  public MemoryConfiguration memory() {
    return request.data().memory();
  }

  @Override
  public LimitsConfiguration limits() {
    return request.data().limits();
  }

  @Override
  public EventHandlingConfiguration events() {
    return request.data().events();
  }

  @Override
  public JobWorkerResponseConfiguration response() {
    return request.data().response();
  }

  public boolean cancelRemainingInstances() {
    return cancelRemainingInstances;
  }

  public void setCancelRemainingInstances(boolean cancelRemainingInstances) {
    this.cancelRemainingInstances = cancelRemainingInstances;
  }
}
