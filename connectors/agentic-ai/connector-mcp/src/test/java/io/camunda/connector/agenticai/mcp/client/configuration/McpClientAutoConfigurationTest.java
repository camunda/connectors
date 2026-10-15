/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.common.configuration.AgenticAiCommonAutoConfiguration;
import io.camunda.connector.agenticai.mcp.client.McpClientFactory;
import io.camunda.connector.agenticai.mcp.client.McpClientFunction;
import io.camunda.connector.agenticai.mcp.client.McpClientRegistry;
import io.camunda.connector.agenticai.mcp.client.McpClientResultDocumentHandler;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientFunction;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientExecutor;
import io.camunda.connector.agenticai.mcp.client.framework.bootstrap.McpClientHeadersSupplierFactory;
import io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.McpSdkClientFactory;
import io.camunda.connector.agenticai.mcp.client.handler.McpClientHandler;
import io.camunda.connector.agenticai.mcp.client.handler.McpRemoteClientHandler;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.http.client.authentication.OAuthClientCredentialsTokenResolver;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class McpClientAutoConfigurationTest {

  // this will need to be updated in case we support different frameworks
  private static final List<Class<?>> SHARED_MCP_CLIENT_BEANS =
      List.of(
          McpClientFactory.class,
          McpClientResultDocumentHandler.class,
          McpClientHeadersSupplierFactory.class,
          McpClientExecutor.class);

  private static final List<Class<?>> MCP_SDK_MCP_CLIENT_BEANS = List.of(McpSdkClientFactory.class);

  private static final List<Class<?>> REMOTE_MCP_CLIENT_BEANS =
      List.of(McpRemoteClientFunction.class, McpRemoteClientHandler.class);

  private static final List<Class<?>> RUNTIME_MCP_CLIENT_BEANS =
      List.of(McpClientFunction.class, McpClientHandler.class, McpClientRegistry.class);

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          .withConfiguration(
              AutoConfigurations.of(
                  AgenticAiCommonAutoConfiguration.class, McpClientAutoConfiguration.class));

  @Test
  void enablesRemoteMcpClientIntegrationByDefault() {
    contextRunner.run(
        context -> {
          assertHasAllBeansOf(context, SHARED_MCP_CLIENT_BEANS);
          assertHasAllBeansOf(context, REMOTE_MCP_CLIENT_BEANS);
        });
  }

  @Test
  void disablesRemoteMcpClientIntegrationIfConfigured() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.mcp.remote-client.enabled=false")
        .run(
            context -> {
              assertDoesNotHaveAnyBeansOf(context, MCP_SDK_MCP_CLIENT_BEANS);
              assertDoesNotHaveAnyBeansOf(context, SHARED_MCP_CLIENT_BEANS);
              assertDoesNotHaveAnyBeansOf(context, REMOTE_MCP_CLIENT_BEANS);
            });
  }

  @Test
  void doesNotEnableRuntimeMcpClientIntegrationByDefault() {
    contextRunner.run(context -> assertDoesNotHaveAnyBeansOf(context, RUNTIME_MCP_CLIENT_BEANS));
  }

  @Test
  void enablesRuntimeMcpClientIntegrationIfConfigured() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.mcp.client.enabled=true")
        .run(
            context -> {
              assertHasAllBeansOfAtLeastOnce(context, SHARED_MCP_CLIENT_BEANS);
              assertHasAllBeansOf(context, RUNTIME_MCP_CLIENT_BEANS);
            });
  }

  @Test
  void doesNotContainAnyMcpClientIntegrationsIfAllDisabled() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.agenticai.mcp.client.enabled=false",
            "camunda.connector.agenticai.mcp.remote-client.enabled=false")
        .run(
            context -> {
              assertDoesNotHaveAnyBeansOf(context, SHARED_MCP_CLIENT_BEANS);
              assertDoesNotHaveAnyBeansOf(context, REMOTE_MCP_CLIENT_BEANS);
              assertDoesNotHaveAnyBeansOf(context, RUNTIME_MCP_CLIENT_BEANS);
            });
  }

  @Test
  void enablesMcpSdkIntegrationIfConfigured() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.agenticai.mcp.client.enabled=true",
            "camunda.connector.agenticai.mcp.remote-client.enabled=false",
            "camunda.connector.agenticai.mcp.client.framework=mcpsdk")
        .run(
            context -> {
              assertHasAllBeansOf(context, RUNTIME_MCP_CLIENT_BEANS);
              assertHasAllBeansOf(context, MCP_SDK_MCP_CLIENT_BEANS);
            });
  }

  @Test
  void enablesMcpSdkRemoteIntegrationIfConfigured() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.mcp.remote-client.framework=mcpsdk")
        .run(
            context -> {
              assertHasAllBeansOf(context, REMOTE_MCP_CLIENT_BEANS);
              assertHasAllBeansOf(context, MCP_SDK_MCP_CLIENT_BEANS);
            });
  }

  private static void assertHasAllBeansOf(
      AssertableApplicationContext context, List<Class<?>> beans) {
    assertAll(beans.stream().map(beanClass -> () -> assertThat(context).hasSingleBean(beanClass)));
  }

  private static void assertHasAllBeansOfAtLeastOnce(
      AssertableApplicationContext context, List<Class<?>> beans) {
    assertAll(
        beans.stream()
            .map(
                beanClass ->
                    () -> assertThat(context).getBeans(beanClass).hasSizeGreaterThanOrEqualTo(1)));
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
