/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.discovery.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.a2a.discovery.A2AClientGatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.a2a.discovery.A2AClientGatewayToolHandler;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.GatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.a2a.discovery.enabled",
    matchIfMissing = true)
public class A2ADiscoveryConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "a2aClientGatewayToolDefinitionResolver")
  public GatewayToolDefinitionResolver a2aClientGatewayToolDefinitionResolver() {
    return new A2AClientGatewayToolDefinitionResolver();
  }

  @Bean
  @ConditionalOnMissingBean(name = "a2aClientGatewayToolHandler")
  public GatewayToolHandler a2aClientGatewayToolHandler(ObjectMapper objectMapper) {
    return new A2AClientGatewayToolHandler(objectMapper);
  }
}
