/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.integration.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.a2a.client.common.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.common.convert.A2aPartConverter;
import io.camunda.connector.agenticai.a2a.client.common.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.configuration.A2aClientAutoConfiguration;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aClientOutboundConnectorFunction;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aClientRequestHandler;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aMessageSender;
import io.camunda.connector.agenticai.a2a.client.outbound.A2aSendMessageResponseHandler;
import io.camunda.connector.agenticai.a2a.client.outbound.convert.A2aDocumentToPartConverter;
import io.camunda.connector.agenticai.a2a.integration.A2aGatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.a2a.integration.A2aGatewayToolHandler;
import io.camunda.connector.agenticai.a2a.integration.systemprompt.A2aSystemPromptContributor;
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

class A2aClientAgenticToolAutoConfigurationTest {

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

  private static final List<Class<?>> A2A_AGENT_TOOL_BEANS =
      List.of(
          A2aGatewayToolDefinitionResolver.class,
          A2aGatewayToolHandler.class,
          A2aSystemPromptContributor.class);

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          .withConfiguration(
              AutoConfigurations.of(
                  AgenticAiCommonAutoConfiguration.class,
                  A2aClientAutoConfiguration.class,
                  A2aClientAgenticToolAutoConfiguration.class));

  @Test
  void enablesA2aAgentToolByDefault() {
    contextRunner.run(context -> assertHasAllBeansOf(context, A2A_AGENT_TOOL_BEANS));
  }

  @Test
  void disablesA2aAgentToolIfConfigured() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.a2a.client.agentic.tool.enabled=false")
        .run(
            context -> {
              assertDoesNotHaveAnyBeansOf(context, A2A_AGENT_TOOL_BEANS);
              assertHasAllBeansOf(context, A2A_COMMON_BEANS);
              assertHasAllBeansOf(context, A2A_OUTBOUND_BEANS);
            });
  }

  @Test
  void keepsA2aAgentToolIfOutboundClientIsDisabled() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.a2a.client.outbound.enabled=false")
        .run(context -> assertHasAllBeansOf(context, A2A_AGENT_TOOL_BEANS));
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
