/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerAgentRequest.JobWorkerAgentRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.api.outbound.JobContext;
import java.util.List;

/** Version-agnostic execution context for the AI Agent Sub-process flavor (serves v1 and v2). */
public class JobWorkerAgentExecutionContext implements AgentExecutionContext {
  private final JobContext jobContext;
  private final JobWorkerAgentRequestData data;
  private final AgentContext initialAgentContext;
  private final List<ToolCallResult> initialToolCallResults;
  private final List<AdHocToolElement> toolElements;
  private final AgentConfiguration configuration;

  public JobWorkerAgentExecutionContext(
      JobContext jobContext,
      JobWorkerAgentRequestData data,
      AgentContext initialAgentContext,
      List<ToolCallResult> initialToolCallResults,
      List<AdHocToolElement> toolElements,
      ChatModelApiConfiguration chatModelApiConfiguration,
      String modelName,
      String modelProvider) {
    this.jobContext = jobContext;
    this.data = data;
    this.initialAgentContext = initialAgentContext;
    this.initialToolCallResults = initialToolCallResults;
    this.toolElements = toolElements;
    this.configuration =
        new AgentConfiguration(
            chatModelApiConfiguration,
            modelName,
            modelProvider,
            data.systemPrompt(),
            data.userPrompt(),
            data.memory(),
            data.limits(),
            data.events(),
            data.response());
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
    return toolElements;
  }

  @Override
  public UserPromptConfiguration userPrompt() {
    return data.userPrompt();
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
    return data.response();
  }
}
