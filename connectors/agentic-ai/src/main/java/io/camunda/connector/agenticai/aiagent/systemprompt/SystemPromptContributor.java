/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.systemprompt;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;

/**
 * Contributes additional instructions to the AI agent's system prompt. Implementations can provide
 * domain-specific, protocol-specific, or context-specific instructions that will be composed with
 * the base system prompt.
 *
 * <p>Contributors are automatically discovered via Spring's dependency injection and composed in
 * order based on {@link #getOrder()}.
 */
public interface SystemPromptContributor {

  /**
   * Returns the additional system prompt content to be appended.
   *
   * @param executionContext The current agent execution context
   * @param agentContext The current agent context
   * @return The system prompt contribution, or null/empty if no contribution needed
   */
  String contributeSystemPrompt(AgentExecutionContext executionContext, AgentContext agentContext);

  /**
   * Determines the order in which this contributor's content is appended. Lower values are appended
   * first.
   *
   * @return The order value (default: 0)
   */
  default int getOrder() {
    return 0;
  }
}
