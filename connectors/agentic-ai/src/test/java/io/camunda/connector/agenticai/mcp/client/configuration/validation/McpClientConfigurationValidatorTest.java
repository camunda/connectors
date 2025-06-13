/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.HttpMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
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
          List.of("echo", "hello"), Collections.emptyMap(), false);
  private static final HttpMcpClientTransportConfiguration HTTP_CONFIGURATION =
      new HttpMcpClientTransportConfiguration(
          "http://localhost:1234/sse", Collections.emptyMap(), Duration.ofSeconds(5), false, false);

  @Autowired private Validator validator;

  @ParameterizedTest
  @MethodSource("validConfigurations")
  void validationSucceedsIfOnlyOneTransportConfigured(McpClientConfiguration configuration) {
    assertThat(validator.validate(configuration)).isEmpty();
  }

  @Test
  void validationFailsWhenBothTransportsConfigured() {
    assertThat(validator.validate(createConfiguration(STDIO_CONFIGURATION, HTTP_CONFIGURATION)))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .containsExactly(
            "The MCP client needs to be configured with a single transport (either STDIO or HTTP)");
  }

  @Test
  void validationFailsWhenNoTransportConfigured() {
    assertThat(validator.validate(createConfiguration(null, null)))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .containsExactly(
            "The MCP client needs to be configured with a single transport (either STDIO or HTTP)");
  }

  static Stream<McpClientConfiguration> validConfigurations() {
    return Stream.of(
        createConfiguration(STDIO_CONFIGURATION, null),
        createConfiguration(null, HTTP_CONFIGURATION));
  }

  private static McpClientConfiguration createConfiguration(
      StdioMcpClientTransportConfiguration stdioConfig,
      HttpMcpClientTransportConfiguration httpConfig) {
    return new McpClientConfiguration(
        true,
        stdioConfig,
        httpConfig,
        Duration.ofSeconds(1),
        Duration.ofSeconds(2),
        Duration.ofSeconds(3));
  }
}
