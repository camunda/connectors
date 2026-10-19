/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientDelegate;

public interface McpClientFactory {
  McpClientDelegate createClient(String clientId, McpClientConfiguration config);

  /**
   * Validates the configuration of a client without creating it, so that misconfiguration fails
   * early instead of on first use.
   *
   * @throws IllegalStateException if the configuration cannot be satisfied
   */
  default void validate(String clientId, McpClientConfiguration config) {}
}
