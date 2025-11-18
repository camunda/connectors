/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.AuthenticationConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.AuthenticationConfiguration.AuthenticationType;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration.McpClientType;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.SseHttpMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.StdioMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.StreamableHttpMcpClientTransportConfiguration;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Import(ValidationAutoConfiguration.class)
class McpClientConfigurationValidatorTest {

  private static final AuthenticationConfiguration NO_AUTHENTICATION =
      AuthenticationConfiguration.builder().type(AuthenticationType.NONE).build();

  private static final StdioMcpClientTransportConfiguration STDIO_CONFIGURATION =
      new StdioMcpClientTransportConfiguration("echo", List.of("hello"), Collections.emptyMap());

  private static final StreamableHttpMcpClientTransportConfiguration STREAMABLE_HTTP_CONFIGURATION =
      new StreamableHttpMcpClientTransportConfiguration(
          "http://localhost:1234/mcp",
          Collections.emptyMap(),
          NO_AUTHENTICATION,
          Duration.ofSeconds(5));

  private static final SseHttpMcpClientTransportConfiguration SSE_CONFIGURATION =
      new SseHttpMcpClientTransportConfiguration(
          "http://localhost:1234/sse",
          Collections.emptyMap(),
          NO_AUTHENTICATION,
          Duration.ofSeconds(5));

  @Autowired private Validator validator;

  @ParameterizedTest
  @MethodSource("validConfigurations")
  void validationSucceeds(McpClientConfiguration configuration) {
    assertThat(validator.validate(configuration)).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("missingTypeConfigurations")
  void validationFailsWhenTypeIsNotConfigured(McpClientConfiguration configuration) {
    assertThat(validator.validate(configuration))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .containsExactlyInAnyOrder("MCP client type must not be null");
  }

  @ParameterizedTest
  @MethodSource("invalidConfigurations")
  void validationFailsWhenTransportForConfiguredTypeIsMissing(
      McpClientConfiguration configuration) {
    assertThat(validator.validate(configuration))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .containsExactly(
            "MCP client transport configuration is missing for the configured type '%s'"
                .formatted(configuration.type()));
  }

  static Stream<McpClientConfiguration> validConfigurations() {
    return Stream.of(
        createConfiguration(McpClientType.STDIO, STDIO_CONFIGURATION, null, null),
        createConfiguration(
            McpClientType.STDIO,
            STDIO_CONFIGURATION,
            STREAMABLE_HTTP_CONFIGURATION,
            SSE_CONFIGURATION),
        createConfiguration(McpClientType.HTTP, null, STREAMABLE_HTTP_CONFIGURATION, null),
        createConfiguration(
            McpClientType.HTTP,
            STDIO_CONFIGURATION,
            STREAMABLE_HTTP_CONFIGURATION,
            SSE_CONFIGURATION),
        createConfiguration(McpClientType.SSE, null, null, SSE_CONFIGURATION),
        createConfiguration(
            McpClientType.SSE,
            STDIO_CONFIGURATION,
            STREAMABLE_HTTP_CONFIGURATION,
            SSE_CONFIGURATION));
  }

  static Stream<McpClientConfiguration> missingTypeConfigurations() {
    return Stream.of(
        createConfiguration(null, null, null, null),
        createConfiguration(
            null, STDIO_CONFIGURATION, STREAMABLE_HTTP_CONFIGURATION, SSE_CONFIGURATION));
  }

  static Stream<McpClientConfiguration> invalidConfigurations() {
    return Stream.of(
        createConfiguration(
            McpClientType.STDIO, null, STREAMABLE_HTTP_CONFIGURATION, SSE_CONFIGURATION),
        createConfiguration(McpClientType.HTTP, STDIO_CONFIGURATION, null, SSE_CONFIGURATION),
        createConfiguration(
            McpClientType.SSE, STDIO_CONFIGURATION, STREAMABLE_HTTP_CONFIGURATION, null));
  }

  private static McpClientConfiguration createConfiguration(
      McpClientType type,
      StdioMcpClientTransportConfiguration stdioConfig,
      StreamableHttpMcpClientTransportConfiguration httpConfig,
      SseHttpMcpClientTransportConfiguration sseConfig) {
    return new McpClientConfiguration(
        true,
        type,
        stdioConfig,
        httpConfig,
        sseConfig,
        Duration.ofSeconds(1),
        Duration.ofSeconds(2),
        Duration.ofSeconds(3));
  }
}
