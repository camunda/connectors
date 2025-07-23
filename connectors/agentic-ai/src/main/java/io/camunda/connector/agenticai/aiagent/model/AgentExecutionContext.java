/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;

public interface AgentExecutionContext {
  /** Job context containing job-specific metadata (such as the element key). */
  AgentJobContext jobContext();

  /** Initial agent context object read from input variables, before further processing. */
  AgentContext initialAgentContext();

  /** Initial tool call results read from input variables, before further processing. */
  List<ToolCallResult> initialToolCallResults();

  ProviderConfiguration provider();

  SystemPromptConfiguration systemPrompt();

  UserPromptConfiguration userPrompt();

  MemoryConfiguration memory();

  LimitsConfiguration limits();

  ResponseConfiguration response();
}
