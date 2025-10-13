/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.inbound.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.a2a.client.api.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.inbound.A2aTaskPollingExecutable;
import io.camunda.connector.agenticai.a2a.inbound.service.A2aTaskPollingExecutorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.a2a.client.polling.task.enabled",
    matchIfMissing = true)
@EnableConfigurationProperties(A2aTaskPollingConfigurationProperties.class)
public class A2aTaskPollingConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public A2aTaskPollingExecutorService a2aTaskPollingExecutorService(
      A2aTaskPollingConfigurationProperties config) {
    return new A2aTaskPollingExecutorService(config.threadPoolSize());
  }

  @Bean
  @Scope("prototype")
  @ConditionalOnMissingBean
  public A2aTaskPollingExecutable a2ATaskPollingExecutable(
      A2aTaskPollingExecutorService executorService,
      A2aSdkClientFactory clientFactory,
      A2aSdkObjectConverter objectConverter,
      ObjectMapper objectMapper) {
    return new A2aTaskPollingExecutable(
        executorService, clientFactory, objectConverter, objectMapper);
  }
}
