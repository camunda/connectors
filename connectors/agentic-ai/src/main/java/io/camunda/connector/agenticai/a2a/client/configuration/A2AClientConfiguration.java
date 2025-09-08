/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.a2a.client.A2AClientAsToolFunction;
import io.camunda.connector.agenticai.a2a.client.A2AClientFunction;
import io.camunda.connector.agenticai.a2a.client.A2AClientHandler;
import io.camunda.connector.agenticai.a2a.client.A2AClientHandlerImpl;
import io.camunda.connector.agenticai.a2a.client.AgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.AgentCardFetcherImpl;
import io.camunda.connector.agenticai.a2a.client.ClientFactory;
import io.camunda.connector.agenticai.a2a.client.ClientFactoryImpl;
import io.camunda.connector.agenticai.a2a.client.MessageSender;
import io.camunda.connector.agenticai.a2a.client.MessageSenderImpl;
import io.camunda.connector.agenticai.a2a.client.SendMessageResponseHandler;
import io.camunda.connector.agenticai.a2a.client.SendMessageResponseHandlerImpl;
import io.camunda.connector.agenticai.a2a.client.TaskPoller;
import io.camunda.connector.agenticai.a2a.client.TaskPollerImpl;
import io.camunda.connector.agenticai.a2a.client.convert.DocumentToPartConverter;
import io.camunda.connector.agenticai.a2a.client.convert.DocumentToPartConverterImpl;
import io.camunda.connector.agenticai.a2a.client.convert.PartsToContentConverter;
import io.camunda.connector.agenticai.a2a.client.convert.PartsToContentConverterImpl;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.a2aclient.enabled",
    matchIfMissing = true)
public class A2AClientConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public DocumentToPartConverter documentToPartConverter(ObjectMapper objectMapper) {
    return new DocumentToPartConverterImpl(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public PartsToContentConverter partsToContentConverter(ObjectMapper objectMapper) {
    return new PartsToContentConverterImpl(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public SendMessageResponseHandler clientEventToContentConverter(
      PartsToContentConverter partsToContentConverter) {
    return new SendMessageResponseHandlerImpl(partsToContentConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public ScheduledExecutorService taskPollerExecutor() {
    return Executors.newScheduledThreadPool(2); // TODO: make pool size configurable
  }

  @Bean
  @ConditionalOnMissingBean
  public TaskPoller taskPoller(SendMessageResponseHandler sendMessageResponseHandler) {
    return new TaskPollerImpl(taskPollerExecutor(), sendMessageResponseHandler);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentCardFetcher agentCardFetcher() {
    return new AgentCardFetcherImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public ClientFactory clientFactory() {
    return new ClientFactoryImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public MessageSender messageSender(
      DocumentToPartConverter documentToPartConverter,
      SendMessageResponseHandler sendMessageResponseHandler,
      TaskPoller taskPoller,
      ClientFactory clientFactory) {
    return new MessageSenderImpl(
        documentToPartConverter, sendMessageResponseHandler, taskPoller, clientFactory);
  }

  @Bean
  @ConditionalOnMissingBean
  public A2AClientHandler a2AClientHandler(
      AgentCardFetcher agentCardFetcher, MessageSender messageSender) {
    return new A2AClientHandlerImpl(agentCardFetcher, messageSender);
  }

  @Bean
  @ConditionalOnMissingBean
  public A2AClientFunction a2AClientFunction(A2AClientHandler handler) {
    return new A2AClientFunction(handler);
  }

  @Bean
  @ConditionalOnMissingBean
  public A2AClientAsToolFunction a2AClientAsToolFunction(A2AClientHandler handler) {
    return new A2AClientAsToolFunction(handler);
  }
}
