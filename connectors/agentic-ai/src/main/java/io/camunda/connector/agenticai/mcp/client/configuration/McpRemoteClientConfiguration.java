/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration;

import io.camunda.connector.agenticai.mcp.client.McpRemoteClientFunction;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry;
import io.camunda.connector.agenticai.mcp.client.configuration.langchain4j.McpRemoteClientLangchain4JFrameworkConfiguration;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientExecutor;
import io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpRemoteClientHandler;
import io.camunda.connector.agenticai.mcp.client.handler.McpRemoteClientHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Configuration for remote MCP clients configured within the process. */
@Configuration
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.mcp.remote-client.enabled",
    matchIfMissing = true)
@EnableConfigurationProperties(McpRemoteClientConfigurationProperties.class)
@Import({
  McpBaseConfiguration.class,
  McpRemoteClientLangchain4JFrameworkConfiguration.class,
})
public class McpRemoteClientConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public McpRemoteClientFunction mcpRemoteClientFunction(McpRemoteClientHandler handler) {
    return new McpRemoteClientFunction(handler);
  }

  @Bean
  @ConditionalOnMissingBean
  public McpRemoteClientHandler langchain4JMcpRemoteClientHandler(
      McpRemoteClientRegistry remoteClientRegistry, McpClientExecutor mcpClientExecutor) {
    return new DefaultMcpRemoteClientHandler(remoteClientRegistry, mcpClientExecutor);
  }
}
