/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.outbound.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.a2a.client.common.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.common.configuration.A2aClientCommonConfiguration;
import io.camunda.connector.agenticai.a2a.client.common.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aClientOutboundConnectorFunction;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aClientRequestHandler;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aClientRequestHandlerImpl;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aMessageSender;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aMessageSenderImpl;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aSendMessageResponseHandler;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aSendMessageResponseHandlerImpl;
import io.camunda.connector.agenticai.a2a.client.outbound.convert.A2aDocumentToPartConverter;
import io.camunda.connector.agenticai.a2a.client.outbound.convert.A2aDocumentToPartConverterImpl;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.a2a.client.outbound.enabled",
    matchIfMissing = true)
@Import(A2aClientCommonConfiguration.class)
public class A2aClientOutboundConnectorConfiguration {
  @Bean
  @ConditionalOnMissingBean
  public A2aDocumentToPartConverter a2aDocumentToPartConverter(
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new A2aDocumentToPartConverterImpl(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aSendMessageResponseHandler a2aSendMessageResponseHandler(
      A2aSdkObjectConverter sdkObjectConverter) {
    return new A2aSendMessageResponseHandlerImpl(sdkObjectConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aMessageSender a2aMessageSender(
      A2aDocumentToPartConverter documentToPartConverter,
      A2aSendMessageResponseHandler sendMessageResponseHandler,
      A2aSdkClientFactory a2ASdkClientFactory) {
    return new A2aMessageSenderImpl(
        documentToPartConverter, sendMessageResponseHandler, a2ASdkClientFactory);
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aClientRequestHandler a2aClientRequestHandler(
      A2aAgentCardFetcher agentCardFetcher,
      A2aMessageSender a2aMessageSender,
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new A2aClientRequestHandlerImpl(agentCardFetcher, a2aMessageSender, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aClientOutboundConnectorFunction a2aClientOutboundConnectorFunction(
      A2aClientRequestHandler handler) {
    return new A2aClientOutboundConnectorFunction(handler);
  }
}
