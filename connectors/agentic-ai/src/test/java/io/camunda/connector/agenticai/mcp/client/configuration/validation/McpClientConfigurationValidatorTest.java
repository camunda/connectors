/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.SseHttpMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.StdioMcpClientTransportConfiguration;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
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

  private static final StdioMcpClientTransportConfiguration STDIO_CONFIGURATION =
      new StdioMcpClientTransportConfiguration(
          "echo", List.of("hello"), Collections.emptyMap(), false);
  private static final SseHttpMcpClientTransportConfiguration SSE_CONFIGURATION =
      new SseHttpMcpClientTransportConfiguration(
          "http://localhost:1234/sse", Collections.emptyMap(), Duration.ofSeconds(5), false, false);

  @Autowired private Validator validator;

  @ParameterizedTest
  @MethodSource("validConfigurations")
  void validationSucceedsIfOnlyOneTransportConfigured(McpClientConfiguration configuration) {
    assertThat(validator.validate(configuration)).isEmpty();
  }

  @Test
  void validationFailsWhenBothTransportsConfigured() {
    assertThat(validator.validate(createConfiguration(STDIO_CONFIGURATION, SSE_CONFIGURATION)))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .containsExactly(
            "The MCP client needs to be configured with a single transport (either STDIO or SSE)");
  }

  @Test
  void validationFailsWhenNoTransportConfigured() {
    assertThat(validator.validate(createConfiguration(null, null)))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .containsExactly(
            "The MCP client needs to be configured with a single transport (either STDIO or SSE)");
  }

  static Stream<McpClientConfiguration> validConfigurations() {
    return Stream.of(
        createConfiguration(STDIO_CONFIGURATION, null),
        createConfiguration(null, SSE_CONFIGURATION));
  }

  private static McpClientConfiguration createConfiguration(
      StdioMcpClientTransportConfiguration stdioConfig,
      SseHttpMcpClientTransportConfiguration sseConfig) {
    return new McpClientConfiguration(
        true,
        stdioConfig,
        sseConfig,
        Duration.ofSeconds(1),
        Duration.ofSeconds(2),
        Duration.ofSeconds(3));
  }
}
