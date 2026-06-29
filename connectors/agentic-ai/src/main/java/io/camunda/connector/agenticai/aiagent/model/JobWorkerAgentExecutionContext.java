/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerAgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.api.outbound.JobContext;
import java.util.List;

public class JobWorkerAgentExecutionContext implements AgentExecutionContext {
  private final JobContext jobContext;
  private final JobWorkerAgentRequest request;
  private final AgentConfiguration configuration;

  public JobWorkerAgentExecutionContext(
      final JobContext jobContext, final JobWorkerAgentRequest request) {
    this.jobContext = jobContext;
    this.request = request;
    this.configuration =
        new AgentConfiguration(
            request.provider(),
            request.data().systemPrompt(),
            request.data().userPrompt(),
            request.data().memory(),
            request.data().limits(),
            request.data().events(),
            request.data().response());
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
  public UserPromptConfiguration userPrompt() {
    return request.data().userPrompt();
  }

  @Override
  public AgentConfiguration configuration() {
    return configuration;
  }

  /**
   * Job-worker-specific response configuration. Exposes {@code includeAgentContext}, which is not
   * part of the generic {@link
   * io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration}.
   */
  public JobWorkerResponseConfiguration response() {
    return request.data().response();
  }
}
