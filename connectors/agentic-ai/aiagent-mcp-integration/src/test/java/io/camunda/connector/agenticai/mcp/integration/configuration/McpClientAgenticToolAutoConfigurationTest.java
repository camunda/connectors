/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.integration.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.common.configuration.AgenticAiCommonAutoConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientAutoConfiguration;
import io.camunda.connector.agenticai.mcp.integration.McpClientGatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.mcp.integration.McpClientGatewayToolHandler;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.http.client.authentication.OAuthClientCredentialsTokenResolver;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class McpClientAgenticToolAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          .withConfiguration(
              AutoConfigurations.of(
                  AgenticAiCommonAutoConfiguration.class,
                  McpClientAutoConfiguration.class,
                  McpClientAgenticToolAutoConfiguration.class));

  @Test
  void enablesMcpAgentToolByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(McpClientGatewayToolDefinitionResolver.class);
          assertThat(context).hasSingleBean(McpClientGatewayToolHandler.class);
        });
  }

  @Test
  void disablesMcpAgentToolIfConfigured() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.mcp.client.agentic.tool.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(McpClientGatewayToolDefinitionResolver.class);
              assertThat(context).doesNotHaveBean(McpClientGatewayToolHandler.class);
            });
  }

  @Test
  void disablesMcpAgentToolIfConfiguredWithDeprecatedProperty() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.mcp.discovery.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(McpClientGatewayToolDefinitionResolver.class);
              assertThat(context).doesNotHaveBean(McpClientGatewayToolHandler.class);
            });
  }

  @Test
  void disablesMcpAgentToolIfEitherPropertyIsFalse() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.agenticai.mcp.client.agentic.tool.enabled=true",
            "camunda.connector.agenticai.mcp.discovery.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(McpClientGatewayToolDefinitionResolver.class);
              assertThat(context).doesNotHaveBean(McpClientGatewayToolHandler.class);
            });
  }

  @Test
  void enablesMcpAgentToolIfDeprecatedPropertyIsTrue() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.mcp.discovery.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpClientGatewayToolDefinitionResolver.class);
              assertThat(context).hasSingleBean(McpClientGatewayToolHandler.class);
            });
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
