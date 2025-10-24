/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.a2a.client.A2aConnectorFunction;
import io.camunda.connector.agenticai.a2a.client.api.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.api.A2aClientFactory;
import io.camunda.connector.agenticai.a2a.client.api.A2aMessageSender;
import io.camunda.connector.agenticai.a2a.client.api.A2aRequestHandler;
import io.camunda.connector.agenticai.a2a.client.api.A2aSendMessageResponseHandler;
import io.camunda.connector.agenticai.a2a.client.convert.A2aDocumentToPartConverter;
import io.camunda.connector.agenticai.a2a.client.convert.A2aDocumentToPartConverterImpl;
import io.camunda.connector.agenticai.a2a.client.convert.A2aPartToContentConverter;
import io.camunda.connector.agenticai.a2a.client.convert.A2aPartToContentConverterImpl;
import io.camunda.connector.agenticai.a2a.client.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.convert.A2aSdkObjectConverterImpl;
import io.camunda.connector.agenticai.a2a.client.impl.A2aAgentCardFetcherImpl;
import io.camunda.connector.agenticai.a2a.client.impl.A2aMessageSenderImpl;
import io.camunda.connector.agenticai.a2a.client.impl.A2aRequestHandlerImpl;
import io.camunda.connector.agenticai.a2a.client.impl.A2aSendMessageResponseHandlerImpl;
import io.camunda.connector.agenticai.a2a.client.sdk.A2aClientFactoryImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.a2a.client.enabled",
    matchIfMissing = true)
@EnableConfigurationProperties(A2aConnectorConfigurationProperties.class)
public class A2aConnectorConfiguration {
  @Bean
  @ConditionalOnMissingBean
  public A2aDocumentToPartConverter a2aDocumentToPartConverter(ObjectMapper objectMapper) {
    return new A2aDocumentToPartConverterImpl(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aPartToContentConverter a2aPartsToContentConverter() {
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
  public A2aSendMessageResponseHandler a2aSendMessageResponseHandler(
      A2aSdkObjectConverter sdkObjectConverter) {
    return new A2aSendMessageResponseHandlerImpl(sdkObjectConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aAgentCardFetcher a2aAgentCardFetcher() {
    return new A2aAgentCardFetcherImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aClientFactory sdkClientFactory(A2aConnectorConfigurationProperties properties) {
    return new A2aClientFactoryImpl(properties.transport());
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aMessageSender a2aMessageSender(
      A2aDocumentToPartConverter documentToPartConverter,
      A2aSendMessageResponseHandler sendMessageResponseHandler,
      A2aClientFactory a2aClientFactory) {
    return new A2aMessageSenderImpl(
        documentToPartConverter, sendMessageResponseHandler, a2aClientFactory);
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aRequestHandler a2aRequestHandler(
      A2aAgentCardFetcher agentCardFetcher,
      A2aMessageSender a2aMessageSender,
      ObjectMapper objectMapper) {
    return new A2aRequestHandlerImpl(agentCardFetcher, a2aMessageSender, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aConnectorFunction a2aConnectorFunction(A2aRequestHandler handler) {
    return new A2aConnectorFunction(handler);
  }
}
