/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration;

import io.camunda.connector.agenticai.mcp.client.McpClientFunction;
import io.camunda.connector.agenticai.mcp.client.McpClientHandler;
import io.camunda.connector.agenticai.mcp.client.McpClientRemoteFunction;
import io.camunda.connector.agenticai.mcp.client.McpClientRemoteHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

// TODO make this more granular (decide which functions to enable)
@Configuration
@ConditionalOnProperty(
    value = "camunda.connector.agenticai.mcp.client.enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(McpClientConfigurationProperties.class)
@Import(McpClientLangchain4JFrameworkConfiguration.class)
public class McpClientConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public McpClientFunction mcpClientFunction(McpClientHandler mcpClientHandler) {
    return new McpClientFunction(mcpClientHandler);
  }

  @Bean
  @ConditionalOnMissingBean
  public McpClientRemoteFunction mcpClientRemoteFunction(McpClientRemoteHandler handler) {
    return new McpClientRemoteFunction(handler);
  }
}
