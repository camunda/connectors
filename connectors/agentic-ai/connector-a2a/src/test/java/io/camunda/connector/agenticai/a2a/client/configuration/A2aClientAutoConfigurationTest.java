/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.a2a.client.common.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.common.convert.A2aPartConverter;
import io.camunda.connector.agenticai.a2a.client.common.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.inbound.polling.A2aClientPollingExecutable;
import io.camunda.connector.agenticai.a2a.client.inbound.polling.service.A2aPollingExecutorService;
import io.camunda.connector.agenticai.a2a.client.inbound.webhook.A2aClientWebhookExecutable;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aClientOutboundConnectorFunction;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aClientRequestHandler;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aMessageSender;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aSendMessageResponseHandler;
import io.camunda.connector.agenticai.a2a.client.outbound.convert.A2aDocumentToPartConverter;
import io.camunda.connector.agenticai.common.configuration.AgenticAiCommonAutoConfiguration;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.http.client.authentication.OAuthClientCredentialsTokenResolver;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class A2aClientAutoConfigurationTest {

  private static final List<Class<?>> A2A_COMMON_BEANS =
      List.of(
          A2aPartConverter.class,
          A2aSdkObjectConverter.class,
          A2aAgentCardFetcher.class,
          A2aSdkClientFactory.class);

  private static final List<Class<?>> A2A_OUTBOUND_BEANS =
      List.of(
          A2aDocumentToPartConverter.class,
          A2aSendMessageResponseHandler.class,
          A2aMessageSender.class,
          A2aClientRequestHandler.class,
          A2aClientOutboundConnectorFunction.class);

  private static final List<Class<?>> A2A_POLLING_BEANS =
      List.of(A2aClientPollingExecutable.class, A2aPollingExecutorService.class);

  private static final List<Class<?>> A2A_WEBHOOK_BEANS = List.of(A2aClientWebhookExecutable.class);

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          .withConfiguration(
              AutoConfigurations.of(
                  AgenticAiCommonAutoConfiguration.class, A2aClientAutoConfiguration.class));

  @Test
  void enablesAllA2aClientIntegrationByDefault() {
    contextRunner.run(
        context -> {
          assertHasAllBeansOf(context, A2A_COMMON_BEANS);
          assertHasAllBeansOf(context, A2A_OUTBOUND_BEANS);
          assertHasAllBeansOf(context, A2A_POLLING_BEANS);
          assertHasAllBeansOf(context, A2A_WEBHOOK_BEANS);
        });
  }

  @Test
  void disablesA2aOutboundClientIfConfigured() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.a2a.client.outbound.enabled=false")
        .run(
            context -> {
              assertDoesNotHaveAnyBeansOf(context, A2A_OUTBOUND_BEANS);
              assertHasAllBeansOf(context, A2A_COMMON_BEANS);
              assertHasAllBeansOf(context, A2A_POLLING_BEANS);
              assertHasAllBeansOf(context, A2A_WEBHOOK_BEANS);
            });
  }

  @Test
  void doesNotContainAnyA2aClientIntegrationsIfAllDisabled() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.agenticai.a2a.client.outbound.enabled=false",
            "camunda.connector.agenticai.a2a.client.polling.enabled=false",
            "camunda.connector.agenticai.a2a.client.webhook.enabled=false")
        .run(
            context -> {
              assertDoesNotHaveAnyBeansOf(context, A2A_OUTBOUND_BEANS);
              assertDoesNotHaveAnyBeansOf(context, A2A_COMMON_BEANS);
              assertDoesNotHaveAnyBeansOf(context, A2A_POLLING_BEANS);
            });
  }

  private static void assertHasAllBeansOf(
      AssertableApplicationContext context, List<Class<?>> beans) {
    assertAll(beans.stream().map(beanClass -> () -> assertThat(context).hasSingleBean(beanClass)));
  }

  private static void assertDoesNotHaveAnyBeansOf(
      AssertableApplicationContext context, List<Class<?>> beans) {
    assertAll(
        beans.stream().map(beanClass -> () -> assertThat(context).doesNotHaveBean(beanClass)));
  }

  static class TestConfig {

    @Bean
    @ConnectorsObjectMapper
    public ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    public OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver() {
      return mock(OAuthClientCredentialsTokenResolver.class);
    }

    @Bean
    public DocumentFactory documentFactory() {
      return mock(DocumentFactory.class);
    }
  }
}
