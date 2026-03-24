/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
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

public class JobWorkerAgentExecutionContext implements AgentExecutionContext {
  private final JobContext jobContext;
  private final JobWorkerAgentRequest request;
  private boolean cancelRemainingInstances;

  public JobWorkerAgentExecutionContext(
      final JobContext jobContext, final JobWorkerAgentRequest request) {
    this.jobContext = jobContext;
    this.request = request;
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
    return request.toolElements();
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
