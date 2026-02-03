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
import io.camunda.connector.agenticai.mcp.client.configuration.annotation.RemoteMcpClientFactory;
import io.camunda.connector.agenticai.mcp.client.framework.bootstrap.McpClientHeadersSupplierFactory;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.Langchain4JMcpClientFactory;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.Langchain4JMcpClientLoggingResolver;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnExpression(
    "'${camunda.connector.agenticai.mcp.remote-client.framework:langchain4j}' == 'langchain4j' and ${camunda.connector.agenticai.mcp.remote-client.enabled:true}")
@Configuration
@EnableConfigurationProperties(McpClientLangchain4JFrameworkConfigurationProperties.class)
public class McpLangchain4JRemoteClientConfiguration {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(McpLangchain4JRemoteClientConfiguration.class);

  @PostConstruct
  void init() {
    LOGGER.info("MCP remote client framework is set to langchain4j");
  }

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
  @RemoteMcpClientFactory
  public McpClientFactory langchain4JMcpRemoteClientFactory(
      @ConnectorsObjectMapper ObjectMapper objectMapper,
      ToolSpecificationConverter toolSpecificationConverter,
      Langchain4JMcpClientLoggingResolver loggingResolver,
      McpClientHeadersSupplierFactory headersSupplierFactory) {
    return new Langchain4JMcpClientFactory(
        loggingResolver, headersSupplierFactory, objectMapper, toolSpecificationConverter);
  }
}
