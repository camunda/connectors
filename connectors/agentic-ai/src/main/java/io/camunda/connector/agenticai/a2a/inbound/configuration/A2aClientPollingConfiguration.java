/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.inbound.configuration;

import io.camunda.connector.agenticai.a2a.client.api.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.inbound.A2aClientPollingExecutable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.a2a.client.polling.enabled",
    matchIfMissing = true)
public class A2aClientPollingConfiguration {

  @Bean
  @Scope("prototype")
  @ConditionalOnMissingBean
  public A2aClientPollingExecutable a2AClientPollingExecutable(
      A2aSdkClientFactory clientFactory, A2aSdkObjectConverter objectConverter) {
    return new A2aClientPollingExecutable(clientFactory, objectConverter);
  }
}
