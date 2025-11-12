/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration.validation;

import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.stream.Stream;

public class McpClientConfigurationValidator
    implements ConstraintValidator<ValidMcpClientConfiguration, McpClientConfiguration> {

  @Override
  public boolean isValid(McpClientConfiguration config, ConstraintValidatorContext cxt) {
    final var stdioConfigured = config.stdio() != null;
    final var httpConfigured = config.http() != null;
    final var sseConfigured = config.sse() != null;

    final var configuredTransports =
        Stream.of(stdioConfigured, httpConfigured, sseConfigured).filter(c -> c).count();

    return configuredTransports == 1L;
  }
}
