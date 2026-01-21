/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration.langchain4j;

import io.camunda.connector.agenticai.mcp.client.McpClientFactory;
import io.camunda.connector.agenticai.mcp.client.McpClientRegistry;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty(
    value = "camunda.connector.agenticai.mcp.client.framework",
    havingValue = "langchain4j",
    matchIfMissing = true)
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.mcp.client.enabled",
    matchIfMissing = false)
@Import(McpClientBaseLangchain4JFrameworkConfiguration.class)
public class McpClientLangchain4JFrameworkConfiguration {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(McpClientLangchain4JFrameworkConfiguration.class);

  @Bean
  @ConditionalOnMissingBean
  public McpClientRegistry langchain4JMcpClientRegistry(
      McpClientConfigurationProperties configuration, McpClientFactory clientFactory) {
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
