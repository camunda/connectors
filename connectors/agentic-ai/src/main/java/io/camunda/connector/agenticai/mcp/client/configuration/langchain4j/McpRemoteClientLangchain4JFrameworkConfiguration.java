/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration.langchain4j;

import io.camunda.connector.agenticai.mcp.client.McpClientFactory;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry;
import io.camunda.connector.agenticai.mcp.client.configuration.McpRemoteClientConfigurationProperties;
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
  public McpRemoteClientRegistry langchain4JMcpRemoteClientRegistry(
      McpRemoteClientConfigurationProperties config, McpClientFactory mcpClientFactory) {
    return new McpRemoteClientRegistry(config.client(), mcpClientFactory);
  }
}
