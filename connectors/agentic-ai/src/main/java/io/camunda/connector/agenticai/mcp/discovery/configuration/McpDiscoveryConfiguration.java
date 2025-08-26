/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.discovery.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.GatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandler;
import io.camunda.connector.agenticai.mcp.discovery.McpClientGatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.mcp.discovery.McpClientGatewayToolHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.mcp.discovery.enabled",
    matchIfMissing = true)
public class McpDiscoveryConfiguration {
  @Bean
  @ConditionalOnMissingBean
  public GatewayToolDefinitionResolver mcpClientGatewayToolDefinitionResolver() {
    return new McpClientGatewayToolDefinitionResolver();
  }

  @Bean
  @ConditionalOnMissingBean
  public GatewayToolHandler mcpClientGatewayToolHandler(ObjectMapper objectMapper) {
    return new McpClientGatewayToolHandler(objectMapper);
  }
}
