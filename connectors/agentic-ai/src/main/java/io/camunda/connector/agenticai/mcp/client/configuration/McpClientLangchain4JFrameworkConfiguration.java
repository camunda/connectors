/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.McpClient;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.mcp.client.McpClientRegistry;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.Langchain4JMcpClientFactory;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.Langchain4JMcpClientHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    value = "camunda.connector.agenticai.mcp.client.framework",
    havingValue = "langchain4j",
    matchIfMissing = true)
public class McpClientLangchain4JFrameworkConfiguration {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(McpClientLangchain4JFrameworkConfiguration.class);

  @Bean
  @ConditionalOnMissingBean
  public Langchain4JMcpClientFactory langchain4JMcpClientFactory() {
    return new Langchain4JMcpClientFactory();
  }

  @Bean
  @ConditionalOnMissingBean
  public McpClientRegistry<McpClient> langchain4JMcpClientRegistry(
      McpClientConfigurationProperties configuration, Langchain4JMcpClientFactory clientFactory) {
    final var registry = new McpClientRegistry<McpClient>();
    configuration
        .clients()
        .forEach(
            (id, clientConfig) -> {
              LOGGER.info("Creating MCP client with ID '{}'", id);
              final var client = clientFactory.createClient(clientConfig);
              registry.register(id, client);
            });

    return registry;
  }

  @Bean
  @ConditionalOnMissingBean
  public Langchain4JMcpClientHandler langchain4JMcpClientHandler(
      McpClientRegistry<McpClient> mcpClientRegistry,
      ToolSpecificationConverter toolSpecificationConverter,
      ObjectMapper objectMapper) {
    return new Langchain4JMcpClientHandler(
        mcpClientRegistry, toolSpecificationConverter, objectMapper);
  }
}
