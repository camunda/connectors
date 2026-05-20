/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import io.camunda.connector.api.error.ConnectorException;

public interface AgentInstanceClient {

  /**
   * Creates an agent instance on the engine, or returns the key of the existing one. The engine
   * command is idempotent by {@code elementInstanceKey}.
   *
   * @throws ConnectorException with code AGENT_INSTANCE_CREATION_FAILED when retries are exhausted
   *     or a non-retryable error occurs
   */
  AgentInstanceKey create(InitialAgentInstanceData params);
}
