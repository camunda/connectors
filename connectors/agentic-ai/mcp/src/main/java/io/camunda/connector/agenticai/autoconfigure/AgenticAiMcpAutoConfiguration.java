/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpRemoteClientConfiguration;
import io.camunda.connector.agenticai.mcp.discovery.configuration.McpDiscoveryConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnBooleanProperty(value = "camunda.connector.agenticai.enabled", matchIfMissing = true)
@Import({
  McpDiscoveryConfiguration.class,
  McpClientConfiguration.class,
  McpRemoteClientConfiguration.class
})
public class AgenticAiMcpAutoConfiguration {}
