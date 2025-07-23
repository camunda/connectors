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

public class McpClientConfigurationValidator
    implements ConstraintValidator<ValidMcpClientConfiguration, McpClientConfiguration> {

  @Override
  public boolean isValid(McpClientConfiguration config, ConstraintValidatorContext cxt) {
    var stdioConfigured = config.stdio() != null;
    var sseConfigured = config.sse() != null;

    // make sure exactly one of the 2 transports is configured
    return stdioConfigured ^ sseConfigured;
  }
}
