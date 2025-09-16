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
import io.camunda.connector.agenticai.a2a.client.DocumentToPartConverter;
import io.camunda.connector.agenticai.a2a.client.DocumentToPartConverterImpl;
import io.camunda.connector.agenticai.a2a.client.PartsToContentConverter;
import io.camunda.connector.agenticai.a2a.client.PartsToContentConverterImpl;
import io.camunda.connector.agenticai.a2a.client.SendMessageResultHandler;
import io.camunda.connector.agenticai.a2a.client.SendMessageResultHandlerImpl;
import io.camunda.connector.agenticai.a2a.client.TaskPoller;
import io.camunda.connector.agenticai.a2a.client.TaskPollerImpl;
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
  public SendMessageResultHandler clientEventToContentConverter(
      PartsToContentConverter partsToContentConverter) {
    return new SendMessageResultHandlerImpl(partsToContentConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public ScheduledExecutorService taskPollerExecutor() {
    return Executors.newScheduledThreadPool(2); // TODO: make pool size configurable
  }

  @Bean
  @ConditionalOnMissingBean
  public TaskPoller taskPoller(SendMessageResultHandler sendMessageResultHandler) {
    return new TaskPollerImpl(taskPollerExecutor(), sendMessageResultHandler);
  }

  @Bean
  @ConditionalOnMissingBean
  public A2AClientHandler a2AClientHandler(
      DocumentToPartConverter documentToPartConverter,
      SendMessageResultHandler sendMessageResultHandler,
      TaskPoller taskPoller) {
    return new A2AClientHandlerImpl(documentToPartConverter, sendMessageResultHandler, taskPoller);
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
