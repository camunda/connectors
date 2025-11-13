/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.outbound.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.a2a.client.common.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.common.A2aClientFactory;
import io.camunda.connector.agenticai.a2a.client.common.configuration.A2aCommonConfiguration;
import io.camunda.connector.agenticai.a2a.client.common.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aMessageSender;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aMessageSenderImpl;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aOutboundConnectorFunction;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aRequestHandler;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aRequestHandlerImpl;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aSendMessageResponseHandler;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aSendMessageResponseHandlerImpl;
import io.camunda.connector.agenticai.a2a.client.outbound.convert.A2aDocumentToPartConverter;
import io.camunda.connector.agenticai.a2a.client.outbound.convert.A2aDocumentToPartConverterImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.a2a.client.outbound.enabled",
    matchIfMissing = true)
@Import(A2aCommonConfiguration.class)
public class A2aOutboundConnectorConfiguration {
  @Bean
  @ConditionalOnMissingBean
  public A2aDocumentToPartConverter a2aDocumentToPartConverter(ObjectMapper objectMapper) {
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
  public A2aOutboundConnectorFunction a2aConnectorFunction(A2aRequestHandler handler) {
    return new A2aOutboundConnectorFunction(handler);
  }
}
