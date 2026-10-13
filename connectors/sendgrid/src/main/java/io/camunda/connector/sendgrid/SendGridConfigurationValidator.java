/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sendgrid;

import com.sendgrid.Method;
import com.sendgrid.Request;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.sendgrid.model.SendGridApiKeyConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates a stored {@link SendGridApiKeyConfiguration} by listing the API key's scopes, a
 * read-only call every valid key may make regardless of its permissions.
 */
public class SendGridConfigurationValidator
    implements ConfigurationValidator<SendGridApiKeyConfiguration> {

  private static final Logger LOG = LoggerFactory.getLogger(SendGridConfigurationValidator.class);

  static final String MISSING_INPUT_MESSAGE = "A SendGrid API key must be provided.";
  static final String UNAUTHORIZED_MESSAGE = "SendGrid rejected the API key (unauthorized).";
  static final String GENERIC_MESSAGE = "The SendGrid credential could not be validated.";

  private final SendGridClientSupplier sendGridSupplier;

  public SendGridConfigurationValidator() {
    this(new SendGridClientSupplier());
  }

  SendGridConfigurationValidator(SendGridClientSupplier sendGridSupplier) {
    this.sendGridSupplier = sendGridSupplier;
  }

  @Override
  public ConfigurationValidationResult validate(SendGridApiKeyConfiguration configuration) {
    if (configuration.apiKey() == null || configuration.apiKey().isBlank()) {
      return ConfigurationValidationResult.failure(ErrorCode.INVALID_INPUT, MISSING_INPUT_MESSAGE);
    }
    final var request = new Request();
    request.setMethod(Method.GET);
    request.setEndpoint("scopes");
    try {
      final int statusCode =
          sendGridSupplier.sendGrid(configuration.apiKey()).api(request).getStatusCode();
      if (statusCode >= 200 && statusCode < 300) {
        return ConfigurationValidationResult.success();
      }
      LOG.debug("SendGrid credential validation failed with status {}", statusCode);
      return statusCode == 401 || statusCode == 403
          ? ConfigurationValidationResult.failure(ErrorCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE)
          : ConfigurationValidationResult.failure(ErrorCode.ERROR, GENERIC_MESSAGE);
    } catch (Exception e) {
      LOG.debug("SendGrid credential validation failed ({})", e.getClass().getName());
      return ConfigurationValidationResult.failure(ErrorCode.ERROR, GENERIC_MESSAGE);
    }
  }
}
