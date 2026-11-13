/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.outbound.JobContext;
import java.util.List;

public interface AgentExecutionContext {
  /** Job context containing job-specific metadata (such as the element key). */
  JobContext jobContext();

  /**
   * The document factory of the job. It resolves and creates documents in the document store of the
   * physical tenant of the job.
   */
  DocumentFactory documentFactory();

  /**
   * The agent instance features (see {@link AgentFeatures}) that a new agent instance of this
   * execution gets. The default is {@link AgentFeatures#ofNewInstance()}. An existing agent
   * instance keeps the features it was created with.
   */
  default List<String> newInstanceFeatures() {
    return AgentFeatures.ofNewInstance();
  }

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
