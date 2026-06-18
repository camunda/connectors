/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.outbound.JobContext;
import java.util.List;

public interface AgentExecutionContext {
  /** Job context containing job-specific metadata (such as the element key). */
  JobContext jobContext();

  /** Initial agent context object read from input variables, before further processing. */
  AgentContext initialAgentContext();

  /** Initial tool call results read from input variables, before further processing. */
  List<ToolCallResult> initialToolCallResults();

  List<AdHocToolElement> toolElements();

  /** Per-invocation user prompt (invocation input, not static configuration). */
  UserPromptConfiguration userPrompt();

  /** Static per-invocation configuration (provider, prompts, memory, limits, events, response). */
  AgentConfiguration configuration();
}
