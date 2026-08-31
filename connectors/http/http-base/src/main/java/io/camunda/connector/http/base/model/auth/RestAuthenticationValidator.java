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
 * Validates a {@link RestAuthenticationConfiguration} out-of-band; shared by REST, GraphQL and HTTP
 * polling. Only the OAuth client-credentials variant is really checked: it carries its own token
 * endpoint, so a token is actually requested. Everything else returns {@link
 * ConfigurationValidationResult#unsupported() unsupported} rather than an unverified success —
 * basic, bearer and API key carry a secret with nothing to present it to, and the refresh-token
 * grant is not read-only, since a provider that rotates refresh tokens would invalidate the very
 * token being checked.
 *
 * <p>Returned messages are static and value-free, and so is the {@code DEBUG} log: only the
 * exception type and error code are recorded, never the throwable, whose message or cause chain can
 * echo provider detail or credential material.
 */
public class RestAuthenticationValidator
    implements ConfigurationValidator<RestAuthenticationConfiguration> {

  private static final Logger LOG = LoggerFactory.getLogger(RestAuthenticationValidator.class);

  static final String MISSING_AUTH_MESSAGE = "Authentication is required.";
  static final String UNAUTHORIZED_MESSAGE =
      "The token endpoint rejected the credential (unauthorized).";
  static final String GENERIC_MESSAGE =
      "The REST authentication credential could not be validated.";

  /** Codes meaning the credential was rejected, not that the endpoint was unreachable. */
  private static final Set<String> UNAUTHORIZED_ERROR_CODES =
      Set.of("401", "403", "OAUTH_REFRESH_TOKEN_EXPIRED", "OAUTH_INTERACTION_REQUIRED");

  /** OAuth error identifiers (RFC 6749 §5.2) for a rejected credential, often sent as 400. */
  private static final Set<String> UNAUTHORIZED_OAUTH_ERRORS =
      Set.of("invalid_client", "invalid_grant", "unauthorized_client");

  @Override
  public ConfigurationValidationResult validate(RestAuthenticationConfiguration configuration) {
    // The only guard: the record carries @Valid but no @NotNull on authentication.
    if (configuration.authentication() == null) {
      return ConfigurationValidationResult.failure(ErrorCode.INVALID_INPUT, MISSING_AUTH_MESSAGE);
    }
    return switch (configuration.authentication()) {
      case NoAuthentication ignored -> ConfigurationValidationResult.success();
      case BasicAuthentication ignored -> ConfigurationValidationResult.unsupported();
      case BearerAuthentication ignored -> ConfigurationValidationResult.unsupported();
      case ApiKeyAuthentication ignored -> ConfigurationValidationResult.unsupported();
      case OAuthAuthentication oauth -> requestToken(oauth);
      case OAuthRefreshTokenAuthentication ignored ->
          ConfigurationValidationResult
              .unsupported(); // Refresh-token rotation can invalidate the token just used
    };
  }

  /** Requests a token as execution does, bypassing the shared cache so no cached token passes. */
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

  /** Maps a failed token request to a result. Package-private so tests can cover every shape. */
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

  /** The OAuth {@code error} identifier from the token endpoint's response body, or null. */
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
