/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.integration.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientAutoConfiguration;
import io.camunda.connector.agenticai.mcp.integration.McpClientGatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.mcp.integration.McpClientGatewayToolHandler;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@AutoConfiguration(after = McpClientAutoConfiguration.class)
@ConditionalOnBooleanProperty(value = "camunda.connector.agenticai.enabled", matchIfMissing = true)
public class McpClientAgenticToolAutoConfiguration {

  static final String ENABLED_PROPERTY =
      "camunda.connector.agenticai.mcp.client.agentic.tool.enabled";

  static final String DEPRECATED_ENABLED_PROPERTY =
      "camunda.connector.agenticai.mcp.discovery.enabled";

  private static final Logger LOGGER =
      LoggerFactory.getLogger(McpClientAgenticToolAutoConfiguration.class);

  public McpClientAgenticToolAutoConfiguration(Environment environment) {
    if (environment.containsProperty(DEPRECATED_ENABLED_PROPERTY)) {
      LOGGER.warn(
          "The property '{}' is deprecated and will be removed in a future release. Use '{}' instead.",
          DEPRECATED_ENABLED_PROPERTY,
          ENABLED_PROPERTY);
    }
  }

  /**
   * The MCP gateway tool beans. Both the property and its deprecated alias must be {@code true} or
   * unset, so setting either of them to {@code false} disables the MCP Client connectors as AI
   * Agent tools.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnBooleanProperty(
      value = {ENABLED_PROPERTY, DEPRECATED_ENABLED_PROPERTY},
      matchIfMissing = true)
  static class McpClientAgenticToolBeans {

    @Bean
    @ConditionalOnMissingBean
    public McpClientGatewayToolDefinitionResolver mcpClientGatewayToolDefinitionResolver() {
      return new McpClientGatewayToolDefinitionResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public McpClientGatewayToolHandler mcpClientGatewayToolHandler(
        @ConnectorsObjectMapper ObjectMapper objectMapper) {
      return new McpClientGatewayToolHandler(objectMapper);
    }
  }
}
