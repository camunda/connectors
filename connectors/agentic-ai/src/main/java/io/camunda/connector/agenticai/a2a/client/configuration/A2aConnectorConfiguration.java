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
import io.camunda.connector.agenticai.a2a.client.api.A2aMessageSender;
import io.camunda.connector.agenticai.a2a.client.api.A2aRequestHandler;
import io.camunda.connector.agenticai.a2a.client.api.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.api.A2aSendMessageResponseHandler;
import io.camunda.connector.agenticai.a2a.client.api.TaskPoller;
import io.camunda.connector.agenticai.a2a.client.convert.DocumentToPartConverter;
import io.camunda.connector.agenticai.a2a.client.convert.DocumentToPartConverterImpl;
import io.camunda.connector.agenticai.a2a.client.convert.PartsToContentConverter;
import io.camunda.connector.agenticai.a2a.client.convert.PartsToContentConverterImpl;
import io.camunda.connector.agenticai.a2a.client.impl.A2aAgentCardFetcherImpl;
import io.camunda.connector.agenticai.a2a.client.impl.A2aMessageSenderImpl;
import io.camunda.connector.agenticai.a2a.client.impl.A2aRequestHandlerImpl;
import io.camunda.connector.agenticai.a2a.client.impl.A2aSendMessageResponseHandlerImpl;
import io.camunda.connector.agenticai.a2a.client.impl.TaskPollerImpl;
import io.camunda.connector.agenticai.a2a.client.sdk.A2aSdkClientFactoryImpl;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.a2a.client.enabled",
    matchIfMissing = true)
public class A2aConnectorConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public DocumentToPartConverter documentToPartConverter(ObjectMapper objectMapper) {
    return new DocumentToPartConverterImpl(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public PartsToContentConverter partsToContentConverter() {
    return new PartsToContentConverterImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aSendMessageResponseHandler a2aSendMessageResponseHandler(
      PartsToContentConverter partsToContentConverter) {
    return new A2aSendMessageResponseHandlerImpl(partsToContentConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public ScheduledExecutorService taskPollerExecutor() {
    return Executors.newScheduledThreadPool(2);
  }

  @Bean
  @ConditionalOnMissingBean
  public TaskPoller taskPoller(A2aSendMessageResponseHandler sendMessageResponseHandler) {
    return new TaskPollerImpl(taskPollerExecutor(), sendMessageResponseHandler);
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aAgentCardFetcher a2aAgentCardFetcher() {
    return new A2aAgentCardFetcherImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aSdkClientFactory sdkClientFactory() {
    return new A2aSdkClientFactoryImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aMessageSender a2aMessageSender(
      DocumentToPartConverter documentToPartConverter,
      A2aSendMessageResponseHandler sendMessageResponseHandler,
      TaskPoller taskPoller,
      A2aSdkClientFactory a2aSdkClientFactory) {
    return new A2aMessageSenderImpl(
        documentToPartConverter, sendMessageResponseHandler, taskPoller, a2aSdkClientFactory);
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aRequestHandler a2aRequestHandler(
      A2aAgentCardFetcher agentCardFetcher, A2aMessageSender a2aMessageSender) {
    return new A2aRequestHandlerImpl(agentCardFetcher, a2aMessageSender);
  }

  @Bean
  @ConditionalOnMissingBean
  public A2aConnectorFunction a2aConnectorFunction(A2aRequestHandler handler) {
    return new A2aConnectorFunction(handler);
  }
}
