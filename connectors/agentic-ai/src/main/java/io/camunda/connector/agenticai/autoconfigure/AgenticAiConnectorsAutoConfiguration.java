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
import io.camunda.connector.agenticai.adhoctoolsschema.feel.FeelInputParamExtractorImpl;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.CachingAdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.CamundaClientAdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.GatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.schema.AdHocToolSchemaGenerator;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.schema.AdHocToolSchemaGeneratorImpl;
import io.camunda.connector.agenticai.aiagent.AiAgentFunction;
import io.camunda.connector.agenticai.aiagent.agent.AgentResponseHandler;
import io.camunda.connector.agenticai.aiagent.agent.AgentResponseHandlerImpl;
import io.camunda.connector.agenticai.aiagent.agent.AiAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.agent.AiAgentRequestHandlerImpl;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.configuration.AgenticAiLangchain4JFrameworkConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandler;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistryImpl;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty(
    value = "camunda.connector.agenticai.enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(AgenticAiConnectorsConfigurationProperties.class)
@Import(AgenticAiLangchain4JFrameworkConfiguration.class)
public class AgenticAiConnectorsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public FeelInputParamExtractor feelInputParamExtractor(ObjectMapper objectMapper) {
    return new FeelInputParamExtractorImpl(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public AdHocToolSchemaGenerator adHocToolSchemaGenerator() {
    return new AdHocToolSchemaGeneratorImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public AdHocToolsSchemaResolver adHocToolsSchemaResolver(
      AgenticAiConnectorsConfigurationProperties configuration,
      CamundaClient camundaClient,
      List<GatewayToolDefinitionResolver> gatewayToolDefinitionResolvers,
      FeelInputParamExtractor feelInputParamExtractor,
      AdHocToolSchemaGenerator adHocToolSchemaGenerator) {

    final var resolver =
        new CamundaClientAdHocToolsSchemaResolver(
            camundaClient,
            gatewayToolDefinitionResolvers,
            feelInputParamExtractor,
            adHocToolSchemaGenerator);

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
  public GatewayToolHandlerRegistry gatewayToolHandlerRegistry(
      List<GatewayToolHandler> gatewayToolHandlers) {
    return new GatewayToolHandlerRegistryImpl(gatewayToolHandlers);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentResponseHandler aiAgentResponseHandler(ObjectMapper objectMapper) {
    return new AgentResponseHandlerImpl(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public AiAgentRequestHandler aiAgentRequestHandler(
      AdHocToolsSchemaResolver schemaResolver,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      AiFrameworkAdapter<?> aiFrameworkAdapter,
      AgentResponseHandler responseHandler) {
    return new AiAgentRequestHandlerImpl(
        schemaResolver, gatewayToolHandlers, aiFrameworkAdapter, responseHandler);
  }

  @Bean
  @ConditionalOnMissingBean
  public AiAgentFunction aiAgentFunction(AiAgentRequestHandler aiAgentRequestHandler) {
    return new AiAgentFunction(aiAgentRequestHandler);
  }
}
