/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.automationanywhere.model.request.AutomationAnywhereConfiguration;
import io.camunda.connector.automationanywhere.model.request.Configuration;
import io.camunda.connector.automationanywhere.model.request.auth.TokenBasedAuthentication;
import io.camunda.connector.http.base.HttpService;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates a stored {@link AutomationAnywhereConfiguration} by logging in to its Control Room. A
 * token credential is left unchecked: it is used as-is, without a login request to verify it.
 */
public class AutomationAnywhereConfigurationValidator
    implements ConfigurationValidator<AutomationAnywhereConfiguration> {

  private static final Logger LOG =
      LoggerFactory.getLogger(AutomationAnywhereConfigurationValidator.class);

  private static final int CONNECTION_TIMEOUT_IN_SECONDS = 10;

  static final String MISSING_INPUT_MESSAGE =
      "A Control Room URL and an authentication must be provided.";
  static final String UNAUTHORIZED_MESSAGE = "The Control Room rejected the login (unauthorized).";
  static final String GENERIC_MESSAGE =
      "The Automation Anywhere credential could not be validated.";

  private static final Set<String> UNAUTHORIZED_ERROR_CODES = Set.of("401", "403");

  private final HttpService httpService;
  private final ObjectMapper objectMapper;

  public AutomationAnywhereConfigurationValidator() {
    this(new HttpService(), ConnectorsObjectMapperSupplier.getCopy());
  }

  AutomationAnywhereConfigurationValidator(HttpService httpService, ObjectMapper objectMapper) {
    this.httpService = httpService;
    this.objectMapper = objectMapper;
  }

  @Override
  public ConfigurationValidationResult validate(AutomationAnywhereConfiguration configuration) {
    if (configuration.authentication() == null
        || configuration.controlRoomUrl() == null
        || configuration.controlRoomUrl().isBlank()) {
      return ConfigurationValidationResult.failure(ErrorCode.INVALID_INPUT, MISSING_INPUT_MESSAGE);
    }
    if (configuration.authentication() instanceof TokenBasedAuthentication) {
      return ConfigurationValidationResult.unsupported();
    }
    var provider =
        AuthenticationFactory.createProvider(
            configuration.authentication(),
            new Configuration(configuration.controlRoomUrl(), CONNECTION_TIMEOUT_IN_SECONDS));
    try {
      var token = provider.obtainToken(httpService, objectMapper);
      return token != null && !token.isBlank()
          ? ConfigurationValidationResult.success()
          : ConfigurationValidationResult.failure(ErrorCode.ERROR, GENERIC_MESSAGE);
    } catch (Exception e) {
      LOG.debug(
          "Automation Anywhere credential validation failed ({}, code {})",
          e.getClass().getName(),
          e instanceof ConnectorException connectorException
              ? connectorException.getErrorCode()
              : "n/a");
      return e instanceof ConnectorException connectorException
              && UNAUTHORIZED_ERROR_CODES.contains(connectorException.getErrorCode())
          ? ConfigurationValidationResult.failure(ErrorCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE)
          : ConfigurationValidationResult.failure(ErrorCode.ERROR, GENERIC_MESSAGE);
    }
  }
}
