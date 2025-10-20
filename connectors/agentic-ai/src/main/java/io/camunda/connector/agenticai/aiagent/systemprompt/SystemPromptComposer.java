/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.systemprompt;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;

/**
 * Composes the final system prompt by combining a base prompt with contributions from registered
 * contributors.
 */
public interface SystemPromptComposer {

  String composeSystemPrompt(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      SystemPromptConfiguration baseSystemPrompt);
}
