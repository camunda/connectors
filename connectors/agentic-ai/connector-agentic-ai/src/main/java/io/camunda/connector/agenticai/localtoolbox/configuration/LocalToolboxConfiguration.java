/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.localtoolbox.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import io.camunda.connector.agenticai.localtoolbox.client.LocalToolboxClientFunction;
import io.camunda.connector.agenticai.localtoolbox.client.LocalToolboxProcessDefinitionResolver;
import io.camunda.connector.agenticai.localtoolbox.discovery.LocalToolboxGatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.localtoolbox.discovery.LocalToolboxGatewayToolHandler;
import io.camunda.connector.agenticai.localtoolbox.router.LocalToolboxRouterFunction;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the local toolbox experiment: opt-in (default off), since it both introspects arbitrary
 * process definitions and creates process instances - same posture as the MCP client connector.
 */
@Configuration
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.local-toolbox.enabled",
    matchIfMissing = false)
public class LocalToolboxConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public LocalToolboxGatewayToolDefinitionResolver localToolboxGatewayToolDefinitionResolver() {
    return new LocalToolboxGatewayToolDefinitionResolver();
  }

  @Bean
  @ConditionalOnMissingBean
  public LocalToolboxGatewayToolHandler localToolboxGatewayToolHandler(
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new LocalToolboxGatewayToolHandler(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public LocalToolboxProcessDefinitionResolver localToolboxProcessDefinitionResolver(
      CamundaClient camundaClient, AgenticAiConnectorsConfigurationProperties configuration) {
    return new LocalToolboxProcessDefinitionResolver(
        camundaClient, configuration.tools().processDefinition().retries());
  }

  @Bean
  @ConditionalOnMissingBean
  public LocalToolboxClientFunction localToolboxClientFunction(
      LocalToolboxProcessDefinitionResolver processDefinitionResolver,
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver,
      AdHocToolsSchemaResolver toolsSchemaResolver,
      CamundaClient camundaClient) {
    return new LocalToolboxClientFunction(
        processDefinitionResolver, toolElementsResolver, toolsSchemaResolver, camundaClient);
  }

  @Bean
  @ConditionalOnMissingBean
  public LocalToolboxRouterFunction localToolboxRouterFunction() {
    return new LocalToolboxRouterFunction();
  }
}
