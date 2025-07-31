/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerAgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;

public class JobWorkerAgentExecutionContext implements AgentExecutionContext {
  private final ActivatedJob job;
  private final JobClient jobClient;
  private final JobWorkerAgentJobContext jobContext;
  private final JobWorkerAgentRequest request;

  public JobWorkerAgentExecutionContext(
      final JobClient jobClient, final ActivatedJob job, final JobWorkerAgentRequest request) {
    this.job = job;
    this.jobClient = jobClient;
    this.jobContext = new JobWorkerAgentJobContext(job);
    this.request = request;
  }

  @Override
  public JobWorkerAgentJobContext jobContext() {
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
  public JobWorkerResponseConfiguration response() {
    return request.data().response();
  }

  public JobClient jobClient() {
    return jobClient;
  }

  public ActivatedJob job() {
    return job;
  }
}
