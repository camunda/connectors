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
    // only validate transport if type is set - otherwise rely on type not null validation
    if (config.type() != null) {
      if (config.transport() == null) {
        cxt.disableDefaultConstraintViolation();
        cxt.buildConstraintViolationWithTemplate(
                "MCP client transport configuration is missing for the configured type '%s'"
                    .formatted(config.type()))
            .addConstraintViolation();
        return false;
      }
    }

    return true;
  }
}
