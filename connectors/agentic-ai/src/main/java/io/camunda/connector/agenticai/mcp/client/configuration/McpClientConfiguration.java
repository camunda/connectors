/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration;

import io.camunda.connector.agenticai.mcp.client.McpClientFactory;
import io.camunda.connector.agenticai.mcp.client.McpClientFunction;
import io.camunda.connector.agenticai.mcp.client.McpClientRegistry;
import io.camunda.connector.agenticai.mcp.client.configuration.annotation.LocalMcpClientFactory;
import io.camunda.connector.agenticai.mcp.client.configuration.langchain4j.McpLangchain4JClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.mcpsdk.McpSdkMcpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientExecutor;
import io.camunda.connector.agenticai.mcp.client.handler.DefaultMcpClientHandler;
import io.camunda.connector.agenticai.mcp.client.handler.McpClientHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration for MCP clients configured on the runtime. As this is not supported in SaaS (yet)
 * without any additional configuration, this is disabled by default.
 */
@Configuration
@ConditionalOnBooleanProperty(value = "camunda.connector.agenticai.mcp.client.enabled")
@EnableConfigurationProperties(McpClientConfigurationProperties.class)
@Import({
  McpBaseConfiguration.class,
  McpLangchain4JClientConfiguration.class,
  McpSdkMcpClientConfiguration.class,
})
public class McpClientConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(McpClientConfiguration.class);

  @Bean
  @ConditionalOnMissingBean
  public McpClientFunction mcpClientFunction(McpClientHandler mcpClientHandler) {
    return new McpClientFunction(mcpClientHandler);
  }

  @Bean
  @ConditionalOnMissingBean
  public McpClientHandler mcpClientHandler(
      McpClientRegistry mcpClientRegistry, McpClientExecutor mcpClientExecutor) {
    return new DefaultMcpClientHandler(mcpClientRegistry, mcpClientExecutor);
  }

  @Bean
  @ConditionalOnMissingBean
  public McpClientRegistry mcpClientRegistry(
      McpClientConfigurationProperties configuration,
      @LocalMcpClientFactory McpClientFactory clientFactory) {
    final var registry = new McpClientRegistry();
    configuration
        .clients()
        .forEach(
            (id, clientConfig) -> {
              if (clientConfig.enabled()) {
                registry.register(
                    id,
                    () -> {
                      LOGGER.info("Creating MCP client with ID '{}'", id);
                      return clientFactory.createClient(id, clientConfig);
                    });
              }
            });

    return registry;
  }
}
