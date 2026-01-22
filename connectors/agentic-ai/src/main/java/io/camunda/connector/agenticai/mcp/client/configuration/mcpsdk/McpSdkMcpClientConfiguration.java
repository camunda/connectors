/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration.mcpsdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.framework.bootstrap.McpClientHeadersSupplierFactory;
import io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.McpSdkClientFactory;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Conditional(McpSdkMcpClientConfiguration.McpSdkFrameworkEnabled.class)
@Configuration
public class McpSdkMcpClientConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(McpSdkMcpClientConfiguration.class);

  @PostConstruct
  void init() {
    LOGGER.info("MCP client framework is set to mcp-sdk");
  }

  @Bean
  @ConditionalOnMissingBean
  public McpSdkClientFactory mcpSdkMcpClientFactory(
      @ConnectorsObjectMapper ObjectMapper objectMapper,
      McpClientHeadersSupplierFactory headersSupplierFactory) {
    return new McpSdkClientFactory(objectMapper, headersSupplierFactory);
  }

  static class McpSdkFrameworkEnabled extends AnyNestedCondition {
    public McpSdkFrameworkEnabled() {
      super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(
        value = "camunda.connector.agenticai.mcp.client.framework",
        havingValue = "mcp-sdk")
    @SuppressWarnings("unused")
    static class McpClientFrameWorkIsMcpSdk {}

    @ConditionalOnProperty(
        value = "camunda.connector.agenticai.mcp.remote-client.framework",
        havingValue = "mcp-sdk")
    @SuppressWarnings("unused")
    static class McpRemoteClientFrameWorkIsMcpSdk {}
  }
}
