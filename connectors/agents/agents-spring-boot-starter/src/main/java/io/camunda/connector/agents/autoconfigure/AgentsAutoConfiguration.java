/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agents.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.agents.core.AgentsApplicationContext;
import io.camunda.connector.agents.core.AgentsApplicationContext.DefaultAgentsApplicationContext;
import io.camunda.connector.agents.core.AgentsApplicationContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AgentsApplicationContext agentsApplicationContext(
      CamundaClient camundaClient, ObjectMapper objectMapper) {
    final var context = new DefaultAgentsApplicationContext(objectMapper, camundaClient);
    AgentsApplicationContextHolder.getInstance().setCurrentContext(context);
    return context;
  }
}
