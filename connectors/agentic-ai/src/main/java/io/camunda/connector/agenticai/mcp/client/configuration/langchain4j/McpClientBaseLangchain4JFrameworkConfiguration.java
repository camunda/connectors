/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.McpClient;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.mcp.client.McpClientFactory;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.Langchain4JMcpClientExecutor;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.Langchain4JMcpClientFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpClientBaseLangchain4JFrameworkConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public McpClientFactory<McpClient> langchain4JMcpClientFactory() {
    return new Langchain4JMcpClientFactory();
  }

  @Bean
  @ConditionalOnMissingBean
  public Langchain4JMcpClientExecutor langchain4JMcpClientExecutor(
      ObjectMapper objectMapper, ToolSpecificationConverter toolSpecificationConverter) {
    return new Langchain4JMcpClientExecutor(objectMapper, toolSpecificationConverter);
  }
}
