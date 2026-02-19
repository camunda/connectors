/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.adhoctoolsschema.AdHocToolsSchemaFunction;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.CachingProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.CamundaClientProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionClient;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.feel.AdHocToolElementParameterExtractor;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.feel.AdHocToolElementParameterExtractorImpl;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolSchemaGenerator;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolSchemaGeneratorImpl;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolverImpl;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.GatewayToolDefinitionResolver;
import io.camunda.zeebe.feel.tagged.impl.TaggedParameterExtractor;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBooleanProperty(value = "camunda.connector.agenticai.enabled", matchIfMissing = true)
@EnableConfigurationProperties(AgenticAiSchemaConfigurationProperties.class)
public class AgenticAiSchemaAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AdHocToolElementParameterExtractor aiAgentAdHocToolElementParameterExtractor() {
    return new AdHocToolElementParameterExtractorImpl(new TaggedParameterExtractor());
  }

  @Bean
  @ConditionalOnMissingBean
  public AdHocToolSchemaGenerator aiAgentAdHocToolSchemaGenerator() {
    return new AdHocToolSchemaGeneratorImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public AdHocToolsSchemaResolver aiAgentAdHocToolDefinitionResolver(
      List<GatewayToolDefinitionResolver> gatewayToolDefinitionResolvers,
      AdHocToolSchemaGenerator schemaGenerator) {
    return new AdHocToolsSchemaResolverImpl(gatewayToolDefinitionResolvers, schemaGenerator);
  }

  @Bean
  @ConditionalOnMissingBean
  public ProcessDefinitionAdHocToolElementsResolver aiAgentProcessDefinitionToolElementsResolver(
      AgenticAiSchemaConfigurationProperties configuration,
      CamundaClient camundaClient,
      AdHocToolElementParameterExtractor parameterExtractor) {
    final var processDefinitionClient =
        new ProcessDefinitionClient(
            camundaClient, configuration.tools().processDefinition().retries());
    final var resolver =
        new CamundaClientProcessDefinitionAdHocToolElementsResolver(
            processDefinitionClient, parameterExtractor);

    final var cacheConfiguration = configuration.tools().processDefinition().cache();
    if (cacheConfiguration.enabled()) {
      return new CachingProcessDefinitionAdHocToolElementsResolver(
          resolver,
          new CachingProcessDefinitionAdHocToolElementsResolver.CacheConfiguration(
              cacheConfiguration.maximumSize(), cacheConfiguration.expireAfterWrite()));
    }

    return resolver;
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBooleanProperty(
      value = "camunda.connector.agenticai.ad-hoc-tools-schema-resolver.enabled",
      matchIfMissing = true)
  public AdHocToolsSchemaFunction aiAgentAdHocToolsSchemaFunction(
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver,
      AdHocToolsSchemaResolver toolsSchemaResolver) {
    return new AdHocToolsSchemaFunction(toolElementsResolver, toolsSchemaResolver);
  }
}
