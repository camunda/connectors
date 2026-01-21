/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.mcp.client.McpClientFactory;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.Langchain4JMcpClientFactory;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.Langchain4JMcpClientHeadersSupplierFactory;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.Langchain4JMcpClientLoggingResolver;
import io.camunda.connector.http.client.authentication.OAuthService;
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty(
        value = "camunda.connector.agenticai.mcp.client.framework",
        havingValue = "langchain4j",
        matchIfMissing = true)
@Configuration
@EnableConfigurationProperties(McpClientLangchain4JFrameworkConfigurationProperties.class)
public class McpClientBaseLangchain4JFrameworkConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public Langchain4JMcpClientLoggingResolver langchain4JMcpClientLoggingResolver(
      McpClientLangchain4JFrameworkConfigurationProperties config) {
    final var loggingConfiguration = config.logging();

    final var resolver = new Langchain4JMcpClientLoggingResolver();
    resolver.setLogStdioEvents(loggingConfiguration.stdio().logEvents());
    resolver.setLogHttpRequests(loggingConfiguration.http().logRequests());
    resolver.setLogHttpResponses(loggingConfiguration.http().logResponses());

    return resolver;
  }

  @Bean
  @ConditionalOnMissingBean
  public Langchain4JMcpClientHeadersSupplierFactory langchain4JMcpClientHeadersSupplierFactory(
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new Langchain4JMcpClientHeadersSupplierFactory(
        new OAuthService(), new CustomApacheHttpClient(), objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public McpClientFactory langchain4JMcpClientFactory(
      @ConnectorsObjectMapper ObjectMapper objectMapper,
      ToolSpecificationConverter toolSpecificationConverter,
      Langchain4JMcpClientLoggingResolver loggingResolver,
      Langchain4JMcpClientHeadersSupplierFactory headersSupplierFactory) {
    return new Langchain4JMcpClientFactory(
        loggingResolver, headersSupplierFactory, objectMapper, toolSpecificationConverter);
  }
}
