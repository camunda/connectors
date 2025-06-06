/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.api.outbound.OutboundConnectorContext;

/** Responsible for initializing the agent context and to initiate tool discovery if necessary. */
public interface AgentInitializer {
  AgentInitializationResult initializeAgent(OutboundConnectorContext context, AgentRequest request);
}
