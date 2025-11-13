/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.configuration;

import io.camunda.connector.agenticai.a2a.client.common.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.common.A2aAgentCardFetcherImpl;
import io.camunda.connector.agenticai.a2a.client.common.convert.A2aPartToContentConverter;
import io.camunda.connector.agenticai.a2a.client.common.convert.A2aPartToContentConverterImpl;
import io.camunda.connector.agenticai.a2a.client.common.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.common.convert.A2aSdkObjectConverterImpl;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClientFactoryImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(A2aCommonConfigurationProperties.class)
public class A2aCommonConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public A2aPartToContentConverter a2aPartToContentConverter() {
    return new A2aPartToContentConverterImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aSdkObjectConverter a2aSdkObjectConverter(
      A2aPartToContentConverter partToContentConverter) {
    return new A2aSdkObjectConverterImpl(partToContentConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aAgentCardFetcher a2aAgentCardFetcher() {
    return new A2aAgentCardFetcherImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aSdkClientFactory sdkClientFactory(A2aCommonConfigurationProperties properties) {
    return new A2aSdkClientFactoryImpl(properties.transport());
  }
}
