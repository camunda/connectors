/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties;

public interface McpClientFactory<C> {
  C createClient(String clientId, McpClientConfigurationProperties.McpClientConfiguration config);
}
