/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.adhoctoolsschema.AdHocToolsSchemaFunction;
import io.camunda.connector.agenticai.adhoctoolsschema.feel.FeelInputParamExtractor;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.CachingAdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.CamundaClientAdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.schema.AdHocToolSchemaGenerator;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.schema.DefaultAdHocToolSchemaGenerator;
import io.camunda.connector.agenticai.aiagent.AiAgentFunction;
import io.camunda.connector.agenticai.aiagent.agent.AiAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.agent.DefaultAiAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.document.CamundaDocumentToContentConverter;
import io.camunda.connector.agenticai.aiagent.provider.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.tools.ToolCallResultConverter;
import io.camunda.connector.agenticai.aiagent.tools.ToolCallingHandler;
import io.camunda.connector.agenticai.aiagent.tools.ToolSpecificationConverter;
import io.camunda.connector.agenticai.mcp.client.McpClientFactory;
import io.camunda.connector.agenticai.mcp.client.McpClientFunction;
import io.camunda.connector.agenticai.mcp.client.McpClientHandler;
import io.camunda.connector.agenticai.mcp.client.McpClientRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    value = "camunda.connector.agenticai.enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties({
  AgenticAiConnectorsConfigurationProperties.class,
  McpClientConfigurationProperties.class
})
public class AgenticAiConnectorsAutoConfiguration {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(AgenticAiConnectorsAutoConfiguration.class);

  @Bean
  @ConditionalOnMissingBean
  public FeelInputParamExtractor feelInputParamExtractor(ObjectMapper objectMapper) {
    return new FeelInputParamExtractor(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public AdHocToolSchemaGenerator adHocToolSchemaGenerator() {
    return new DefaultAdHocToolSchemaGenerator();
  }

  @Bean
  @ConditionalOnMissingBean
  public AdHocToolsSchemaResolver adHocToolsSchemaResolver(
      AgenticAiConnectorsConfigurationProperties configuration,
      CamundaClient camundaClient,
      FeelInputParamExtractor feelInputParamExtractor,
      AdHocToolSchemaGenerator adHocToolSchemaGenerator) {

    final var resolver =
        new CamundaClientAdHocToolsSchemaResolver(
            camundaClient, feelInputParamExtractor, adHocToolSchemaGenerator);

    final var cacheConfiguration = configuration.tools().cache();
    if (cacheConfiguration.enabled()) {
      return new CachingAdHocToolsSchemaResolver(
          resolver,
          new CachingAdHocToolsSchemaResolver.CacheConfiguration(
              cacheConfiguration.maximumSize(), cacheConfiguration.expireAfterWrite()));
    }

    return resolver;
  }

  @Bean
  @ConditionalOnMissingBean
  public AdHocToolsSchemaFunction adHocToolsSchemaFunction(
      AdHocToolsSchemaResolver schemaResolver) {
    return new AdHocToolsSchemaFunction(schemaResolver);
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatModelFactory chatModelFactory() {
    return new ChatModelFactory();
  }

  @Bean
  @ConditionalOnMissingBean
  public ToolSpecificationConverter toolSpecificationConverter() {
    return new ToolSpecificationConverter();
  }

  @Bean
  @ConditionalOnMissingBean
  public ToolCallingHandler toolCallingHandler(
      ObjectMapper objectMapper,
      AdHocToolsSchemaResolver schemaResolver,
      ToolSpecificationConverter toolSpecificationConverter) {
    return new ToolCallingHandler(objectMapper, schemaResolver, toolSpecificationConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public CamundaDocumentToContentConverter camundaDocumentConverter() {
    return new CamundaDocumentToContentConverter();
  }

  @Bean
  @ConditionalOnMissingBean
  public ToolCallResultConverter toolCallResultConverter(
      ObjectMapper objectMapper, CamundaDocumentToContentConverter documentConverter) {
    return new ToolCallResultConverter(objectMapper, documentConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public AiAgentRequestHandler aiAgentRequestHandler(
      ObjectMapper objectMapper,
      ChatModelFactory chatModelFactory,
      AdHocToolsSchemaResolver schemaResolver,
      ToolSpecificationConverter toolSpecificationConverter,
      ToolCallingHandler toolCallingHandler,
      ToolCallResultConverter toolCallResultConverter,
      CamundaDocumentToContentConverter documentConverter) {
    return new DefaultAiAgentRequestHandler(
        objectMapper,
        chatModelFactory,
        schemaResolver,
        toolSpecificationConverter,
        toolCallingHandler,
        toolCallResultConverter,
        documentConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public AiAgentFunction aiAgentFunction(AiAgentRequestHandler aiAgentRequestHandler) {
    return new AiAgentFunction(aiAgentRequestHandler);
  }

  @Bean
  @ConditionalOnMissingBean
  public McpClientRegistry mcpClientRegistry(McpClientConfigurationProperties configuration) {
    final var factory = new McpClientFactory();
    final var registry = new McpClientRegistry();
    configuration
        .clients()
        .forEach(
            (id, clientConfig) -> {
              LOGGER.info("Creating MCP client with ID '{}'", id);
              final var client = factory.createClient(clientConfig);
              registry.register(id, client);
            });

    return registry;
  }

  @Bean
  @ConditionalOnMissingBean
  public McpClientHandler mcpClientHandler(
      McpClientRegistry mcpClientRegistry,
      ObjectMapper objectMapper,
      ToolSpecificationConverter toolSpecificationConverter) {
    return new McpClientHandler(mcpClientRegistry, objectMapper, toolSpecificationConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public McpClientFunction mcpClientFunction(McpClientHandler mcpClientHandler) {
    return new McpClientFunction(mcpClientHandler);
  }
}
