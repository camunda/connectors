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
import io.camunda.connector.agenticai.aiagent.request.AgentSubProcessRequestData;
import io.camunda.connector.agenticai.aiagent.translation.AgentConfigurationTranslator;
import io.camunda.connector.api.outbound.JobContext;
import java.util.List;

public class AgentSubProcessExecutionContext implements AgentExecutionContext {
  private final JobContext jobContext;
  private final AgentSubProcessRequestData data;
  private final AgentContext initialAgentContext;
  private final List<ToolCallResult> initialToolCallResults;
  private final List<AdHocToolElement> toolElements;
  private final AgentConfiguration configuration;

  public AgentSubProcessExecutionContext(
      JobContext jobContext,
      AgentSubProcessRequestData data,
      AgentContext initialAgentContext,
      List<ToolCallResult> initialToolCallResults,
      List<AdHocToolElement> toolElements,
      ChatModelConfiguration chatModel) {
    this.jobContext = jobContext;
    this.data = data;
    this.initialAgentContext = initialAgentContext;
    this.initialToolCallResults = initialToolCallResults;
    this.toolElements = toolElements;
    this.configuration = AgentConfigurationTranslator.translate(chatModel, data);
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
    return configuration.userPrompt();
  }

  @Override
  public AgentConfiguration configuration() {
    return configuration;
  }

  /**
   * Whether the agent context is part of the sub-process response variable. Specific to the job
   * worker flavor, it is not part of the generic response configuration.
   */
  public boolean includeAgentContext() {
    return data.response() != null && Boolean.TRUE.equals(data.response().includeAgentContext());
  }
}
