/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta.outbound;

import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.email.client.jakarta.utils.EmailServerLoginValidation;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import io.camunda.connector.email.config.EmailAccountConfiguration;

/**
 * Validates a stored {@link EmailAccountConfiguration} by logging in to the single server it
 * carries.
 */
public class EmailAccountValidator implements ConfigurationValidator<EmailAccountConfiguration> {

  private final JakartaUtils jakartaUtils = new JakartaUtils();

  @Override
  public ConfigurationValidationResult validate(EmailAccountConfiguration configuration) {
    return EmailServerLoginValidation.login(
        jakartaUtils, configuration.getConfiguration(), configuration.toAuthentication());
  }
}
