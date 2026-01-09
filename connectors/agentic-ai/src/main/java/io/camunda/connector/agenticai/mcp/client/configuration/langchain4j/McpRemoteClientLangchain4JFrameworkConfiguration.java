/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration.langchain4j;

import dev.langchain4j.mcp.client.McpClient;
import io.camunda.connector.agenticai.mcp.client.McpClientFactory;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientHandler;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry;
import io.camunda.connector.agenticai.mcp.client.configuration.McpRemoteClientConfigurationProperties;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.Langchain4JMcpRemoteClientHandler;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc.Langchain4JMcpClientExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty(
    value = "camunda.connector.agenticai.mcp.remote-client.framework",
    havingValue = "langchain4j",
    matchIfMissing = true)
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.mcp.remote-client.enabled",
    matchIfMissing = true)
@Import(McpClientBaseLangchain4JFrameworkConfiguration.class)
public class McpRemoteClientLangchain4JFrameworkConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public McpRemoteClientRegistry<McpClient> langchain4JMcpRemoteClientRegistry(
      McpRemoteClientConfigurationProperties config, McpClientFactory<McpClient> mcpClientFactory) {
    return new McpRemoteClientRegistry<>(config.client(), mcpClientFactory);
  }

  @Bean
  @ConditionalOnMissingBean
  public McpRemoteClientHandler langchain4JMcpRemoteClientHandler(
      McpRemoteClientRegistry<McpClient> remoteClientRegistry,
      Langchain4JMcpClientExecutor mcpClientExecutor) {
    return new Langchain4JMcpRemoteClientHandler(remoteClientRegistry, mcpClientExecutor);
  }
}
