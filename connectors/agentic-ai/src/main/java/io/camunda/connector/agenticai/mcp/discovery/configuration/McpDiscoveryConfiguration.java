/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.discovery.configuration;

import io.camunda.connector.agenticai.adhoctoolsschema.resolver.GatewayToolDefinitionResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    value = "camunda.connector.agenticai.mcp.discovery.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class McpDiscoveryConfiguration {
  @Bean
  @ConditionalOnMissingBean
  public GatewayToolDefinitionResolver mcpClientGatewayToolDefinitionResolver() {
    return new McpClientGatewayToolDefinitionResolver();
  }
}
