/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model.auth;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.http.client.authentication.OAuthConstants;
import io.camunda.connector.http.client.authentication.OAuthService;
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Only the OAuth client-credentials variant can be checked out-of-band: it carries its own token
 * endpoint, so a token is actually requested. Basic, bearer and API key hold a secret with nothing
 * to present it to, and checking the refresh-token grant would consume a token the provider may
 * rotate — both return {@link ConfigurationValidationResult#unsupported() unsupported} rather than
 * an unverified success.
 */
public class RestAuthenticationValidator
    implements ConfigurationValidator<RestAuthenticationConfiguration> {

  private static final Logger LOG = LoggerFactory.getLogger(RestAuthenticationValidator.class);

  static final String MISSING_AUTH_MESSAGE = "Authentication is required.";
  static final String UNAUTHORIZED_MESSAGE =
      "The token endpoint rejected the credential (unauthorized).";
  static final String GENERIC_MESSAGE =
      "The REST authentication credential could not be validated.";

  private static final Set<String> UNAUTHORIZED_ERROR_CODES =
      Set.of("401", "403", "OAUTH_REFRESH_TOKEN_EXPIRED", "OAUTH_INTERACTION_REQUIRED");

  private static final Set<String> UNAUTHORIZED_OAUTH_ERRORS =
      Set.of("invalid_client", "invalid_grant", "unauthorized_client");

  @Override
  public ConfigurationValidationResult validate(RestAuthenticationConfiguration configuration) {
    if (configuration.authentication() == null) {
      return ConfigurationValidationResult.failure(ErrorCode.INVALID_INPUT, MISSING_AUTH_MESSAGE);
    }
    return switch (configuration.authentication()) {
      case NoAuthentication ignored -> ConfigurationValidationResult.success();
      case BasicAuthentication ignored -> ConfigurationValidationResult.unsupported();
      case BearerAuthentication ignored -> ConfigurationValidationResult.unsupported();
      case ApiKeyAuthentication ignored -> ConfigurationValidationResult.unsupported();
      case OAuthAuthentication oauth -> requestToken(oauth);
      case OAuthRefreshTokenAuthentication ignored -> ConfigurationValidationResult.unsupported();
    };
  }

  private static ConfigurationValidationResult requestToken(OAuthAuthentication authentication) {
    try {
      var oAuthService = new OAuthService();
      var mapped =
          (io.camunda.connector.http.client.model.auth.OAuthAuthentication)
              AuthenticationMapper.map(authentication);
      new CustomApacheHttpClient()
          .execute(
              oAuthService.createOAuthRequestFrom(mapped), oAuthService::extractTokenFromResponse);
      return ConfigurationValidationResult.success();
    } catch (Exception e) {
      LOG.debug(
          "Token request failed for a REST authentication credential (type {}, code {})",
          e.getClass().getName(),
          e instanceof ConnectorException connectorException
              ? connectorException.getErrorCode()
              : "n/a");
      return classifyFailure(e);
    }
  }

  static ConfigurationValidationResult classifyFailure(Exception e) {
    return e instanceof ConnectorException connectorException
            && isCredentialRejected(connectorException)
        ? ConfigurationValidationResult.failure(ErrorCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE)
        : ConfigurationValidationResult.failure(ErrorCode.ERROR, GENERIC_MESSAGE);
  }

  private static boolean isCredentialRejected(ConnectorException e) {
    String code = e.getErrorCode();
    String oauthError = oauthErrorOf(e);
    return (code != null && UNAUTHORIZED_ERROR_CODES.contains(code))
        || (oauthError != null && UNAUTHORIZED_OAUTH_ERRORS.contains(oauthError));
  }

  private static String oauthErrorOf(ConnectorException e) {
    Map<String, Object> variables = e.getErrorVariables();
    if (variables != null
        && variables.get("response") instanceof Map<?, ?> response
        && response.get("body") instanceof Map<?, ?> body
        && body.get(OAuthConstants.ERROR) instanceof String error) {
      return error;
    }
    return null;
  }
}
