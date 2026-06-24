/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.discovery.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.sandbox.discovery.SandboxGatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.sandbox.discovery.SandboxGatewayToolHandler;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.sandbox.discovery.enabled",
    matchIfMissing = true)
public class SandboxDiscoveryConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public SandboxGatewayToolDefinitionResolver sandboxGatewayToolDefinitionResolver() {
    return new SandboxGatewayToolDefinitionResolver();
  }

  @Bean
  @ConditionalOnMissingBean
  public SandboxGatewayToolHandler sandboxGatewayToolHandler(
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new SandboxGatewayToolHandler(objectMapper);
  }
}
