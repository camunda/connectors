/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration.mcpsdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.common.AgenticAiHttpSupport;
import io.camunda.connector.agenticai.mcp.client.configuration.annotation.RuntimeMcpClientFactory;
import io.camunda.connector.agenticai.mcp.client.framework.bootstrap.McpClientHeadersSupplierFactory;
import io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.McpSdkClientFactory;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnExpression(
    "'${camunda.connector.agenticai.mcp.client.framework:mcpsdk}' == 'mcpsdk' and ${camunda.connector.agenticai.mcp.client.enabled:false}")
@Configuration
public class McpSdkMcpClientConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(McpSdkMcpClientConfiguration.class);

  @PostConstruct
  void init() {
    LOGGER.info("MCP client framework is set to mcpsdk");
  }

  @Bean
  @RuntimeMcpClientFactory
  public McpSdkClientFactory mcpSdkMcpClientFactory(
      @ConnectorsObjectMapper ObjectMapper objectMapper,
      AgenticAiHttpSupport httpSupport,
      McpClientHeadersSupplierFactory headersSupplierFactory) {
    return new McpSdkClientFactory(
        objectMapper, httpSupport.getJdkHttpClientProxyConfigurator(), headersSupplierFactory);
  }
}
