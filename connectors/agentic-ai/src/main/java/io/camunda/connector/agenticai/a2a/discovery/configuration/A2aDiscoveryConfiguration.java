/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.discovery.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.a2a.discovery.A2aGatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.a2a.discovery.A2aGatewayToolHandler;
import io.camunda.connector.agenticai.a2a.discovery.systemprompt.A2aSystemPromptContributor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.a2a.discovery.enabled",
    matchIfMissing = true)
public class A2aDiscoveryConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public A2aGatewayToolDefinitionResolver a2aGatewayToolDefinitionResolver() {
    return new A2aGatewayToolDefinitionResolver();
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aGatewayToolHandler a2aGatewayToolHandler(ObjectMapper objectMapper) {
    return new A2aGatewayToolHandler(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aSystemPromptContributor a2aSystemPromptContributor() {
    return new A2aSystemPromptContributor();
  }
}
