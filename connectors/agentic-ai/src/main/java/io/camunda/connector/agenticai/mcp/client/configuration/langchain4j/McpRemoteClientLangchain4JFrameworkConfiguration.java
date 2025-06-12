/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.Langchain4JMcpClientExecutor;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.Langchain4JMcpClientFactory;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.Langchain4JMcpClientRemoteHandler;
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
@Import(McpClientBaseLangchain4JFrameworkConfiguration.class)
public class McpRemoteClientLangchain4JFrameworkConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public Langchain4JMcpClientRemoteHandler langchain4JMcpClientRemoteHandler(
      ObjectMapper objectMapper,
      Langchain4JMcpClientFactory mcpClientFactory,
      Langchain4JMcpClientExecutor mcpClientExecutor) {
    return new Langchain4JMcpClientRemoteHandler(objectMapper, mcpClientFactory, mcpClientExecutor);
  }
}
