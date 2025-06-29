/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.api.outbound.OutboundConnectorContext;

/**
 * Main entry point for handling agent requests from an outbound connector.
 *
 * <p>The agent response is expected to either return an agent response or to contain a list of tool
 * calls to handle through the process.
 */
public interface AiAgentRequestHandler {
  AgentResponse handleRequest(OutboundConnectorContext context, AgentRequest request);
}
