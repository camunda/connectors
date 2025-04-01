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
import org.springframework.context.ApplicationContext;

public class SpringBasedAgentsApplicationContext implements AgentsApplicationContext {

  private final ApplicationContext applicationContext;

  public SpringBasedAgentsApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Override
  public ObjectMapper objectMapper() {
    return applicationContext.getBean(ObjectMapper.class);
  }

  @Override
  public CamundaClient camundaClient() {
    return applicationContext.getBean(CamundaClient.class);
  }
}
