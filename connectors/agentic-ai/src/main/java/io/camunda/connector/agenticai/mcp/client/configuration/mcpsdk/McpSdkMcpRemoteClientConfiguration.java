/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration.mcpsdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.common.AgenticAiHttpSupport;
import io.camunda.connector.agenticai.mcp.client.configuration.annotation.RemoteMcpClientFactory;
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
    "'${camunda.connector.agenticai.mcp.remote-client.framework:mcpsdk}' == 'mcpsdk' and ${camunda.connector.agenticai.mcp.remote-client.enabled:true}")
@Configuration
public class McpSdkMcpRemoteClientConfiguration {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(McpSdkMcpRemoteClientConfiguration.class);

  @PostConstruct
  void init() {
    LOGGER.info("MCP remote client framework is set to mcpsdk");
  }

  @Bean
  @RemoteMcpClientFactory
  public McpSdkClientFactory mcpSdkMcpRemoteClientFactory(
      @ConnectorsObjectMapper ObjectMapper objectMapper,
      AgenticAiHttpSupport httpSupport,
      McpClientHeadersSupplierFactory headersSupplierFactory) {
    return new McpSdkClientFactory(
        objectMapper, httpSupport.getJdkHttpClientProxyConfigurator(), headersSupplierFactory);
  }
}
