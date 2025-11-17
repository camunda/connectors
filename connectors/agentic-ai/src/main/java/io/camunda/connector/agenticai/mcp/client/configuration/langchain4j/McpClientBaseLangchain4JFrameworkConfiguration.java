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
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.Langchain4JMcpClientHeadersSupplierFactory;
import io.camunda.connector.http.client.authentication.OAuthService;
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpClientBaseLangchain4JFrameworkConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public Langchain4JMcpClientHeadersSupplierFactory langchain4JMcpClientHeadersSupplierFactory(
      ObjectMapper objectMapper) {
    return new Langchain4JMcpClientHeadersSupplierFactory(
        new OAuthService(), new CustomApacheHttpClient(), objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public McpClientFactory<McpClient> langchain4JMcpClientFactory(
      Langchain4JMcpClientHeadersSupplierFactory headersSupplierFactory) {
    return new Langchain4JMcpClientFactory(headersSupplierFactory);
  }

  @Bean
  @ConditionalOnMissingBean
  public Langchain4JMcpClientExecutor langchain4JMcpClientExecutor(
      ObjectMapper objectMapper, ToolSpecificationConverter toolSpecificationConverter) {
    return new Langchain4JMcpClientExecutor(objectMapper, toolSpecificationConverter);
  }
}
