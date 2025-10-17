/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.inbound.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.a2a.client.api.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.api.A2aClientFactory;
import io.camunda.connector.agenticai.a2a.client.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.inbound.A2aPollingExecutable;
import io.camunda.connector.agenticai.a2a.inbound.service.A2aPollingExecutorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.a2a.client.polling.enabled",
    matchIfMissing = true)
@EnableConfigurationProperties(A2aPollingConfigurationProperties.class)
public class A2aPollingConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public A2aPollingExecutorService a2aPollingExecutorService(
      A2aPollingConfigurationProperties config) {
    return new A2aPollingExecutorService(config.threadPoolSize());
  }

  @Bean
  @Scope("prototype")
  @ConditionalOnMissingBean
  public A2aPollingExecutable a2APollingExecutable(
      A2aPollingExecutorService executorService,
      A2aAgentCardFetcher agentCardFetcher,
      A2aClientFactory clientFactory,
      A2aSdkObjectConverter objectConverter,
      ObjectMapper objectMapper) {
    return new A2aPollingExecutable(
        executorService, agentCardFetcher, clientFactory, objectConverter, objectMapper);
  }
}
