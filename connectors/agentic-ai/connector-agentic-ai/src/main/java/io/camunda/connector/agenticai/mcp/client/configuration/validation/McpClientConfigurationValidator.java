/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration.validation;

import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.InMemoryMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

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

    if (config.type() != null
        && config.transport() instanceof InMemoryMcpClientTransportConfiguration inMemory) {
      final var beanName = inMemory.serverBeanName();
      final var className = inMemory.serverClassName();
      if ((beanName == null) == (className == null)
          || !hasText(beanName != null ? beanName : className)) {
        cxt.disableDefaultConstraintViolation();
        cxt.buildConstraintViolationWithTemplate(
                "Exactly one of 'server-bean-name' and 'server-class-name' must be set (and not blank), the other must be unset, for an IN_MEMORY MCP client")
            .addConstraintViolation();
        return false;
      }
    }

    return true;
  }

  private static boolean hasText(@Nullable String value) {
    return value != null && !value.isBlank();
  }
}
