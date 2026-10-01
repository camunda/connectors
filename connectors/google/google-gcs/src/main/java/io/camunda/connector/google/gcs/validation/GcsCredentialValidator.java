/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.google.gcs.validation;

import com.google.api.client.http.HttpResponseException;
import com.google.auth.oauth2.ServiceAccountCredentials;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.google.gcs.model.request.Authentication;
import io.camunda.connector.google.gcs.model.request.GcsCredentialConfiguration;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcsCredentialValidator implements ConfigurationValidator<GcsCredentialConfiguration> {

  private static final Logger LOG = LoggerFactory.getLogger(GcsCredentialValidator.class);

  private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";

  static final String UNAUTHORIZED_MESSAGE = "Google rejected the credential (unauthorized).";
  static final String GENERIC_MESSAGE =
      "The Google Cloud Storage credential could not be validated.";
  static final String MISSING_AUTH_MESSAGE = "A service account key is required.";

  @FunctionalInterface
  interface CredentialCheck {
    void run(String jsonKey) throws Exception;
  }

  private final CredentialCheck credentialCheck;

  public GcsCredentialValidator() {
    this(GcsCredentialValidator::acquireToken);
  }

  GcsCredentialValidator(CredentialCheck credentialCheck) {
    this.credentialCheck = credentialCheck;
  }

  @Override
  public ConfigurationValidationResult validate(GcsCredentialConfiguration configuration) {
    Authentication authentication = configuration.authentication();
    if (authentication == null
        || authentication.getJsonKey() == null
        || authentication.getJsonKey().isBlank()) {
      return ConfigurationValidationResult.failure(ErrorCode.INVALID_INPUT, MISSING_AUTH_MESSAGE);
    }
    try {
      credentialCheck.run(authentication.getJsonKey());
      return ConfigurationValidationResult.success();
    } catch (Exception e) {
      if (isRejectedByGoogle(e)) {
        LOG.debug("Google rejected the credential ({})", e.getClass().getName());
        return ConfigurationValidationResult.failure(ErrorCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
      }
      LOG.debug("Google Cloud Storage credential validation failed ({})", e.getClass().getName());
      return ConfigurationValidationResult.failure(ErrorCode.ERROR, GENERIC_MESSAGE);
    }
  }

  private static boolean isRejectedByGoogle(Throwable throwable) {
    for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
      if (cause instanceof HttpResponseException http && isRejectionStatus(http.getStatusCode())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isRejectionStatus(int statusCode) {
    return statusCode == 400 || statusCode == 401 || statusCode == 403;
  }

  private static void acquireToken(String jsonKey) throws Exception {
    ServiceAccountCredentials credentials =
        ServiceAccountCredentials.fromStream(
            new ByteArrayInputStream(jsonKey.getBytes(StandardCharsets.UTF_8)));
    credentials.createScoped(List.of(SCOPE)).refresh();
  }
}
