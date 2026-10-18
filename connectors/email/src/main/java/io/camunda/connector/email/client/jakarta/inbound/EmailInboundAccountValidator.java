/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta.inbound;

import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.email.client.jakarta.utils.EmailServerLoginValidation;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import io.camunda.connector.email.config.EmailInboundAccountConfiguration;

/** Validates a stored {@link EmailInboundAccountConfiguration} by logging in to its IMAP server. */
public class EmailInboundAccountValidator
    implements ConfigurationValidator<EmailInboundAccountConfiguration> {

  private final JakartaUtils jakartaUtils = new JakartaUtils();

  @Override
  public ConfigurationValidationResult validate(EmailInboundAccountConfiguration configuration) {
    return EmailServerLoginValidation.login(
        jakartaUtils, configuration.toImapConfig(), configuration.toAuthentication());
  }
}
