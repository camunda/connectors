/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.core.AgentsApplicationContext;
import io.camunda.connector.agenticai.core.AgentsApplicationContextHolder;
import io.camunda.connector.agenticai.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.schema.CachingAdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.schema.CamundaClientAdHocToolsSchemaResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentsConnectorsConfiguration.class)
public class AgentsConnectorsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AgentsApplicationContext agentsApplicationContext(ApplicationContext applicationContext) {
    final var context = new SpringBasedAgentsApplicationContext(applicationContext);
    AgentsApplicationContextHolder.setCurrentContext(context);
    return context;
  }

  @Bean
  @ConditionalOnMissingBean
  public AdHocToolsSchemaResolver adHocToolsSchemaResolver(
      AgentsConnectorsConfiguration configuration, CamundaClient camundaClient) {
    final var resolver = new CamundaClientAdHocToolsSchemaResolver(camundaClient);

    final var cacheConfiguration = configuration.tools().cache();
    if (cacheConfiguration.enabled()) {
      return new CachingAdHocToolsSchemaResolver(
          resolver,
          new CachingAdHocToolsSchemaResolver.CacheConfiguration(
              cacheConfiguration.maxSize(), cacheConfiguration.expireAfterWrite()));
    }

    return resolver;
  }
}
