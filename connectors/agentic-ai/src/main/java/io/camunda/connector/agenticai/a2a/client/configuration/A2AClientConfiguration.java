/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.configuration;

import io.camunda.connector.agenticai.a2a.client.A2AClientFunction;
import io.camunda.connector.agenticai.a2a.client.A2AClientHandler;
import io.camunda.connector.agenticai.a2a.client.A2AClientHandlerImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class A2AClientConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public A2AClientHandler a2AClientHandler() {
    return new A2AClientHandlerImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public A2AClientFunction a2AClientFunction(A2AClientHandler handler) {
    return new A2AClientFunction(handler);
  }
}
