/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;

/**
 * Primarily responsible for initializing the agent context and to resolve tool call results from
 * the request.
 *
 * <p>Can interact with gateway tool handlers to initiate a tool discovery process (e.g. call MCP
 * client activities to list tools).
 */
public interface AgentInitializer {
  AgentInitializationResult initializeAgent(AgentExecutionContext executionContext);
}
