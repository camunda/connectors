/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.inbound.webhook.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.a2a.client.common.configuration.A2aClientCommonConfiguration;
import io.camunda.connector.agenticai.a2a.client.common.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.inbound.webhook.A2aClientWebhookExecutable;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;

@Configuration
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.a2a.client.webhook.enabled",
    matchIfMissing = true)
@Import(A2aClientCommonConfiguration.class)
public class A2aClientWebhookConfiguration {

  @Bean
  @Scope("prototype")
  @ConditionalOnMissingBean
  public A2aClientWebhookExecutable a2aClientWebhookExecutable(
      A2aSdkObjectConverter objectConverter, @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new A2aClientWebhookExecutable(objectConverter, objectMapper);
  }
}
