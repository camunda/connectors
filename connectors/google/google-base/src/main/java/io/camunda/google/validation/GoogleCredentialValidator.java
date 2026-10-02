/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.google.validation;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.oauth2.UserCredentials;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.google.model.Authentication;
import io.camunda.google.model.GoogleCredentialConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleCredentialValidator
    implements ConfigurationValidator<GoogleCredentialConfiguration> {

  private static final Logger LOG = LoggerFactory.getLogger(GoogleCredentialValidator.class);

  private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo";

  static final String UNAUTHORIZED_MESSAGE = "Google rejected the credential (unauthorized).";
  static final String GENERIC_MESSAGE = "The Google credential could not be validated.";
  static final String MISSING_AUTH_MESSAGE = "Authentication is required.";

  @FunctionalInterface
  interface TokenCheck {
    void run(Authentication authentication) throws Exception;
  }

  private final TokenCheck tokenCheck;

  public GoogleCredentialValidator() {
    this(GoogleCredentialValidator::acquireToken);
  }

  GoogleCredentialValidator(TokenCheck tokenCheck) {
    this.tokenCheck = tokenCheck;
  }

  @Override
  public ConfigurationValidationResult validate(GoogleCredentialConfiguration configuration) {
    Authentication authentication = configuration.authentication();
    if (authentication == null || authentication.authType() == null) {
      return ConfigurationValidationResult.failure(ErrorCode.INVALID_INPUT, MISSING_AUTH_MESSAGE);
    }
    switch (authentication.authType()) {
      case BEARER, REFRESH -> {}
      default -> {
        return ConfigurationValidationResult.unsupported();
      }
    }
    try {
      tokenCheck.run(authentication);
      return ConfigurationValidationResult.success();
    } catch (Exception e) {
      if (isRejectedByGoogle(e)) {
        LOG.debug("Google rejected the credential ({})", e.getClass().getName());
        return ConfigurationValidationResult.failure(ErrorCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
      }
      LOG.debug("Google credential validation failed ({})", e.getClass().getName());
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

  private static void acquireToken(Authentication authentication) throws Exception {
    switch (authentication.authType()) {
      case BEARER -> introspectBearerToken(authentication.bearerToken());
      case REFRESH -> refreshAccessToken(authentication);
      default -> {}
    }
  }

  private static void refreshAccessToken(Authentication authentication) throws Exception {
    UserCredentials credentials =
        UserCredentials.newBuilder()
            .setClientId(authentication.oauthClientId())
            .setClientSecret(authentication.oauthClientSecret())
            .setRefreshToken(authentication.oauthRefreshToken())
            .build();
    credentials.refresh();
  }

  private static void introspectBearerToken(String bearerToken) throws Exception {
    GenericUrl url = new GenericUrl(TOKENINFO_URL);
    url.put("access_token", bearerToken);
    HttpResponse response =
        new NetHttpTransport().createRequestFactory().buildGetRequest(url).execute();
    response.disconnect();
  }
}
