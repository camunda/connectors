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
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.CachingAdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.CamundaClientAdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.aiagent.AiAgentFunction;
import io.camunda.connector.agenticai.aiagent.agent.AiAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.agent.DefaultAiAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.provider.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.tools.ToolCallingHandler;
import io.camunda.connector.agenticai.aiagent.tools.ToolSpecificationConverter;
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
@EnableConfigurationProperties(AgenticAiConnectorsConfigurationProperties.class)
public class AgenticAiConnectorsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AdHocToolsSchemaResolver adHocToolsSchemaResolver(
      AgenticAiConnectorsConfigurationProperties configuration,
      CamundaClient camundaClient,
      ObjectMapper objectMapper) {
    final var resolver = new CamundaClientAdHocToolsSchemaResolver(camundaClient, objectMapper);

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
  public AiAgentRequestHandler aiAgentRequestHandler(
      ObjectMapper objectMapper,
      ChatModelFactory chatModelFactory,
      ToolCallingHandler toolCallingHandler) {
    return new DefaultAiAgentRequestHandler(objectMapper, chatModelFactory, toolCallingHandler);
  }

  @Bean
  @ConditionalOnMissingBean
  public AiAgentFunction aiAgentFunction(AiAgentRequestHandler aiAgentRequestHandler) {
    return new AiAgentFunction(aiAgentRequestHandler);
  }
}
