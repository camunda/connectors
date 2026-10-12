/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.common.auth;

import com.azure.core.credential.TokenRequestContext;
import com.azure.core.exception.ClientAuthenticationException;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.aad.msal4j.MsalServiceException;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.api.validation.ConfigurationValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MicrosoftEntraCredentialValidator
    implements ConfigurationValidator<MicrosoftEntraConfiguration> {

  private static final Logger LOG =
      LoggerFactory.getLogger(MicrosoftEntraCredentialValidator.class);
  private static final String DEFAULT_SCOPE = "https://graph.microsoft.com/.default";

  static final String UNAUTHORIZED_MESSAGE =
      "Microsoft Entra ID rejected the credential (unauthorized).";
  static final String GENERIC_MESSAGE = "The Microsoft Entra ID credential could not be validated.";
  static final String MISSING_AUTH_MESSAGE = "Authentication is required.";

  @FunctionalInterface
  interface TokenCheck {
    void run(ClientCredentialsAuthentication authentication);
  }

  private final TokenCheck tokenCheck;

  public MicrosoftEntraCredentialValidator() {
    this(MicrosoftEntraCredentialValidator::acquireToken);
  }

  MicrosoftEntraCredentialValidator(TokenCheck tokenCheck) {
    this.tokenCheck = tokenCheck;
  }

  @Override
  public ConfigurationValidationResult validate(MicrosoftEntraConfiguration configuration) {
    MicrosoftAuthentication authentication = configuration.authentication();
    if (authentication == null) {
      return ConfigurationValidationResult.failure(ErrorCode.INVALID_INPUT, MISSING_AUTH_MESSAGE);
    }
    if (!(authentication instanceof ClientCredentialsAuthentication clientCredentials)) {
      return ConfigurationValidationResult.unsupported();
    }
    try {
      tokenCheck.run(clientCredentials);
      return ConfigurationValidationResult.success();
    } catch (Exception e) {
      if (isRejectedByEntra(e)) {
        LOG.debug("Microsoft Entra ID rejected the credential ({})", e.getClass().getName());
        return ConfigurationValidationResult.failure(ErrorCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
      }
      LOG.debug("Microsoft Entra ID credential validation failed ({})", e.getClass().getName());
      return ConfigurationValidationResult.failure(ErrorCode.ERROR, GENERIC_MESSAGE);
    }
  }

  private static boolean isRejectedByEntra(Throwable throwable) {
    for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
      if (cause instanceof ClientAuthenticationException authenticationException
          && authenticationException.getResponse() != null
          && isRejectionStatus(authenticationException.getResponse().getStatusCode())) {
        return true;
      }
      if (cause instanceof MsalServiceException msal
          && msal.statusCode() != null
          && isRejectionStatus(msal.statusCode())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isRejectionStatus(int statusCode) {
    return statusCode == 400 || statusCode == 401 || statusCode == 403;
  }

  private static void acquireToken(ClientCredentialsAuthentication authentication) {
    ClientSecretCredential credential =
        new ClientSecretCredentialBuilder()
            .tenantId(authentication.tenantId())
            .clientId(authentication.clientId())
            .clientSecret(authentication.clientSecret())
            .build();
    credential.getTokenSync(new TokenRequestContext().addScopes(DEFAULT_SCOPE));
  }
}
