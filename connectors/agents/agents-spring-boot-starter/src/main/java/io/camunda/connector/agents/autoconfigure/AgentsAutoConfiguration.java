/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agents.autoconfigure;

import io.camunda.connector.agents.core.AgentsApplicationContext;
import io.camunda.connector.agents.core.AgentsApplicationContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AgentsApplicationContext agentsApplicationContext(ApplicationContext applicationContext) {
    final var context = new SpringBasedAgentsApplicationContext(applicationContext);
    AgentsApplicationContextHolder.setCurrentContext(context);
    return context;
  }
}
