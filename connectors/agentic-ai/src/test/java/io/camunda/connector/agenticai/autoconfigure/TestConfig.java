/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.document.store.CamundaDocumentStore;
import io.camunda.spring.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.spring.client.metrics.DefaultNoopMetricsRecorder;
import io.camunda.spring.client.metrics.MetricsRecorder;
import org.springframework.context.annotation.Bean;

class TestConfig {
  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean
  public CamundaClient camundaClient() {
    return mock(CamundaClient.class);
  }

  @Bean
  public DocumentFactory documentFactory() {
    return mock(DocumentFactory.class);
  }

  @Bean
  public CamundaDocumentStore documentStore() {
    return mock(CamundaDocumentStore.class);
  }

  @Bean
  public FeelEngineWrapper feelEngineWrapper() {
    return mock(FeelEngineWrapper.class);
  }

  @Bean
  public SecretProviderAggregator secretProviderAggregator() {
    return mock(SecretProviderAggregator.class);
  }

  @Bean
  public CommandExceptionHandlingStrategy commandExceptionHandlingStrategy() {
    return mock(CommandExceptionHandlingStrategy.class);
  }

  @Bean
  public MetricsRecorder metricsRecorder() {
    return new DefaultNoopMetricsRecorder();
  }
}
