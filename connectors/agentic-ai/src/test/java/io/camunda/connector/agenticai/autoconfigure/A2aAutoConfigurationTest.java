/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import static io.camunda.connector.agenticai.autoconfigure.ApplicationContextAssertions.assertDoesNotHaveAnyBeansOf;
import static io.camunda.connector.agenticai.autoconfigure.ApplicationContextAssertions.assertHasAllBeansOf;

import io.camunda.connector.agenticai.a2a.agenttool.A2aGatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.a2a.agenttool.A2aGatewayToolHandler;
import io.camunda.connector.agenticai.a2a.agenttool.systemprompt.A2aSystemPromptContributor;
import io.camunda.connector.agenticai.a2a.client.A2aConnectorFunction;
import io.camunda.connector.agenticai.a2a.client.api.A2aMessageSender;
import io.camunda.connector.agenticai.a2a.client.api.A2aRequestHandler;
import io.camunda.connector.agenticai.a2a.client.api.A2aSendMessageResponseHandler;
import io.camunda.connector.agenticai.a2a.client.convert.A2aDocumentToPartConverter;
import io.camunda.connector.agenticai.a2a.common.api.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.common.api.A2aClientFactory;
import io.camunda.connector.agenticai.a2a.common.convert.A2aPartToContentConverter;
import io.camunda.connector.agenticai.a2a.common.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.inbound.polling.A2aPollingExecutable;
import io.camunda.connector.agenticai.a2a.inbound.polling.service.A2aPollingExecutorService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class A2aAutoConfigurationTest {

  private static final List<Class<?>> A2A_COMMON_BEANS =
      List.of(
          A2aPartToContentConverter.class,
          A2aSdkObjectConverter.class,
          A2aAgentCardFetcher.class,
          A2aClientFactory.class);

  private static final List<Class<?>> A2A_CLIENT_BEANS =
      List.of(
          A2aDocumentToPartConverter.class,
          A2aSendMessageResponseHandler.class,
          A2aMessageSender.class,
          A2aRequestHandler.class,
          A2aConnectorFunction.class);

  private static final List<Class<?>> A2A_DISCOVERY_BEANS =
      List.of(
          A2aGatewayToolDefinitionResolver.class,
          A2aGatewayToolHandler.class,
          A2aSystemPromptContributor.class);

  private static final List<Class<?>> A2A_POLLING_BEANS =
      List.of(A2aPollingExecutable.class, A2aPollingExecutorService.class);

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          .withUserConfiguration(AgenticAiConnectorsAutoConfiguration.class);

  @Test
  void enablesAllA2aIntegrationByDefault() {
    contextRunner.run(
        context -> {
          assertHasAllBeansOf(context, A2A_COMMON_BEANS);
          assertHasAllBeansOf(context, A2A_CLIENT_BEANS);
          assertHasAllBeansOf(context, A2A_DISCOVERY_BEANS);
          assertHasAllBeansOf(context, A2A_POLLING_BEANS);
        });
  }

  @Test
  void disablesA2aDiscoveryIfConfigured() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.a2a.agenttool.enabled=false")
        .run(
            context -> {
              assertDoesNotHaveAnyBeansOf(context, A2A_DISCOVERY_BEANS);
              assertHasAllBeansOf(context, A2A_COMMON_BEANS);
              assertHasAllBeansOf(context, A2A_CLIENT_BEANS);
            });
  }

  @Test
  void disablesA2aClientIfConfigured() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.a2a.client.enabled=false")
        .run(
            context -> {
              assertDoesNotHaveAnyBeansOf(context, A2A_CLIENT_BEANS);
              assertHasAllBeansOf(context, A2A_COMMON_BEANS);
              assertHasAllBeansOf(context, A2A_DISCOVERY_BEANS);
              assertHasAllBeansOf(context, A2A_POLLING_BEANS);
            });
  }

  @Test
  void doesNotContainAnyA2aIntegrationsIfAllDisabled() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.agenticai.a2a.client.enabled=false",
            "camunda.connector.agenticai.a2a.agenttool.enabled=false",
            "camunda.connector.agenticai.a2a.client.polling.enabled=false")
        .run(
            context -> {
              assertDoesNotHaveAnyBeansOf(context, A2A_CLIENT_BEANS);
              assertDoesNotHaveAnyBeansOf(context, A2A_DISCOVERY_BEANS);
              assertDoesNotHaveAnyBeansOf(context, A2A_COMMON_BEANS);
              assertDoesNotHaveAnyBeansOf(context, A2A_POLLING_BEANS);
            });
  }
}
