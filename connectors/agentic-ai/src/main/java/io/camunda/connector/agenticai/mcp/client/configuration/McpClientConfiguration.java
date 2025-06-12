/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration;

import io.camunda.connector.agenticai.mcp.client.McpClientFunction;
import io.camunda.connector.agenticai.mcp.client.McpClientHandler;
import io.camunda.connector.agenticai.mcp.client.configuration.langchain4j.McpClientLangchain4JFrameworkConfiguration;
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
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.mcp.client.enabled",
    matchIfMissing = false)
@EnableConfigurationProperties(McpClientConfigurationProperties.class)
@Import(McpClientLangchain4JFrameworkConfiguration.class)
public class McpClientConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(McpClientConfiguration.class);

  @Bean
  @ConditionalOnMissingBean
  public McpClientFunction mcpClientFunction(
      McpClientConfigurationProperties config, McpClientHandler mcpClientHandler) {
    LOGGER.debug("Creating McpClientFunction with framework {}", config.framework());
    return new McpClientFunction(mcpClientHandler);
  }
}
