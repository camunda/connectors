/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.configuration;

import io.camunda.connector.agenticai.a2a.client.A2AClientRequestHandlerImpl;
import io.camunda.connector.agenticai.a2a.client.A2aClientFunction;
import io.camunda.connector.agenticai.a2a.client.A2aClientRequestHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class A2aClientConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public A2aClientRequestHandler a2aClientRequestHandler() {
    return new A2AClientRequestHandlerImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aClientFunction a2aClientFunction(A2aClientRequestHandler handler) {
    return new A2aClientFunction(handler);
  }
}
