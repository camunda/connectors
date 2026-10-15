/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.tools.schema.configuration;

import io.camunda.connector.agenticai.adhoctoolsschema.schema.GatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.common.configuration.AgenticAiCommonAutoConfiguration;
import io.camunda.connector.agenticai.tools.schema.AdHocToolSchemaGenerator;
import io.camunda.connector.agenticai.tools.schema.AdHocToolSchemaGeneratorImpl;
import io.camunda.connector.agenticai.tools.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.tools.schema.AdHocToolsSchemaResolverImpl;
import io.camunda.connector.agenticai.tools.schema.processdefinition.CachingProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.tools.schema.processdefinition.CamundaClientProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.tools.schema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.tools.schema.processdefinition.ProcessDefinitionClient;
import io.camunda.connector.agenticai.tools.schema.processdefinition.feel.AdHocToolElementParameterExtractor;
import io.camunda.connector.agenticai.tools.schema.processdefinition.feel.AdHocToolElementParameterExtractorImpl;
import io.camunda.connector.runtime.tenant.PhysicalTenantClientSelector;
import io.camunda.zeebe.feel.tagged.impl.TaggedParameterExtractor;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = AgenticAiCommonAutoConfiguration.class)
@ConditionalOnBooleanProperty(value = "camunda.connector.agenticai.enabled", matchIfMissing = true)
@EnableConfigurationProperties(ToolsSchemaProperties.class)
public class AgenticAiToolsSchemaAutoConfiguration {

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
      ToolsSchemaProperties toolsSchemaProperties,
      PhysicalTenantClientSelector clientSelector,
      AdHocToolElementParameterExtractor parameterExtractor) {
    final var processDefinitionClient =
        new ProcessDefinitionClient(
            clientSelector, toolsSchemaProperties.processDefinition().retries());
    final var resolver =
        new CamundaClientProcessDefinitionAdHocToolElementsResolver(
            processDefinitionClient, parameterExtractor);

    final var cacheConfiguration = toolsSchemaProperties.processDefinition().cache();
    if (cacheConfiguration.enabled()) {
      return new CachingProcessDefinitionAdHocToolElementsResolver(
          resolver,
          new CachingProcessDefinitionAdHocToolElementsResolver.CacheConfiguration(
              cacheConfiguration.maximumSize(), cacheConfiguration.expireAfterWrite()));
    }

    return resolver;
  }
}
