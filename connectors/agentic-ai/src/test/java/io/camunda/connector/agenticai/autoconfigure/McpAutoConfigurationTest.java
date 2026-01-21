/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import static io.camunda.connector.agenticai.autoconfigure.ApplicationContextAssertions.assertDoesNotHaveAnyBeansOf;
import static io.camunda.connector.agenticai.autoconfigure.ApplicationContextAssertions.assertHasAllBeansOf;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.mcp.client.*;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.Langchain4JMcpClientHeadersSupplierFactory;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.Langchain4JMcpClientLoggingResolver;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientExecutor;
import io.camunda.connector.agenticai.mcp.client.handler.McpClientHandler;
import io.camunda.connector.agenticai.mcp.client.handler.McpRemoteClientHandler;
import io.camunda.connector.agenticai.mcp.discovery.McpClientGatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.mcp.discovery.McpClientGatewayToolHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class McpAutoConfigurationTest {

  // this will need to be updated in case we support different frameworks
  private static final List<Class<?>> SHARED_MCP_CLIENT_BEANS =
      List.of(
          McpClientFactory.class,
          McpClientResultDocumentHandler.class,
          Langchain4JMcpClientLoggingResolver.class,
          Langchain4JMcpClientHeadersSupplierFactory.class,
          McpClientExecutor.class);

  private static final List<Class<?>> REMOTE_MCP_CLIENT_BEANS =
      List.of(McpRemoteClientFunction.class, McpRemoteClientHandler.class);

  private static final List<Class<?>> RUNTIME_MCP_CLIENT_BEANS =
      List.of(McpClientFunction.class, McpClientHandler.class, McpClientRegistry.class);

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          .withUserConfiguration(AgenticAiConnectorsAutoConfiguration.class);

  @Test
  void enablesMcpDiscoveryByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(McpClientGatewayToolDefinitionResolver.class);
          assertThat(context).hasSingleBean(McpClientGatewayToolHandler.class);
        });
  }

  @Test
  void disablesMcpDiscoveryIfConfigured() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.mcp.discovery.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(McpClientGatewayToolDefinitionResolver.class);
              assertThat(context).doesNotHaveBean(McpClientGatewayToolHandler.class);
            });
  }

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
              assertDoesNotHaveAnyBeansOf(context, SHARED_MCP_CLIENT_BEANS);
              assertDoesNotHaveAnyBeansOf(context, REMOTE_MCP_CLIENT_BEANS);
            });
  }

  @Test
  void doesNotEnableRuntimeMcpClientIntegrationByDefault() {
    contextRunner.run(
        context -> {
          assertDoesNotHaveAnyBeansOf(context, RUNTIME_MCP_CLIENT_BEANS);
        });
  }

  @Test
  void enablesRuntimeMcpClientIntegrationIfConfigured() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.mcp.client.enabled=true")
        .run(
            context -> {
              assertHasAllBeansOf(context, SHARED_MCP_CLIENT_BEANS);
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
}
