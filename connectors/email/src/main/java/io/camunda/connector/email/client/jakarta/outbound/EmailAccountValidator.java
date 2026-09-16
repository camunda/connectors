/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta.outbound;

import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.api.validation.ConfigurationValidationResult.Status;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.email.client.jakarta.utils.EmailServerLoginValidation;
import io.camunda.connector.email.client.jakarta.utils.JakartaUtils;
import io.camunda.connector.email.config.Configuration;
import io.camunda.connector.email.config.EmailAccountConfiguration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Validates a stored {@link EmailAccountConfiguration} by logging in to every server it carries.
 * The account is usable only if all of them accept it, so the first failure decides the verdict.
 */
public class EmailAccountValidator implements ConfigurationValidator<EmailAccountConfiguration> {

  static final String MISSING_SERVER_MESSAGE =
      "Configure at least one of the SMTP, IMAP or POP3 servers, so this email account can be validated.";

  private final JakartaUtils jakartaUtils = new JakartaUtils();

  @Override
  public ConfigurationValidationResult validate(EmailAccountConfiguration configuration) {
    List<Configuration> servers =
        Stream.of(
                configuration.toSmtpConfig(),
                configuration.toImapConfig(),
                configuration.toPop3Config())
            .filter(Objects::nonNull)
            .map(Configuration.class::cast)
            .toList();
    if (servers.isEmpty()) {
      return ConfigurationValidationResult.failure(ErrorCode.INVALID_INPUT, MISSING_SERVER_MESSAGE);
    }
    var authentication = configuration.toAuthentication();
    for (Configuration server : servers) {
      ConfigurationValidationResult result =
          EmailServerLoginValidation.login(jakartaUtils, server, authentication);
      if (result.status() != Status.SUCCESS) {
        return result;
      }
    }
    return ConfigurationValidationResult.success();
  }
}
