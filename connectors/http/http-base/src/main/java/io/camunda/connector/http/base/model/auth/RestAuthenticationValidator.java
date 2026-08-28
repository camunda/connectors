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
import io.camunda.connector.http.client.model.auth.HttpAuthentication;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates a {@link RestAuthenticationConfiguration} out-of-band; shared by REST, GraphQL and HTTP
 * polling. The OAuth variants carry their own token endpoint, so a token is really requested;
 * basic, bearer and API key carry a secret with nothing to present it to, so they return {@link
 * ConfigurationValidationResult#unsupported() unsupported} rather than an unverified success.
 *
 * <p>Returned messages are static and value-free; the full exception is logged at {@code DEBUG}.
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

  /** Seam for testing: requests a token for the given OAuth variant, throwing on failure. */
  @FunctionalInterface
  interface TokenRequest {
    void run(Authentication authentication);
  }

  private final TokenRequest tokenRequest;

  public RestAuthenticationValidator() {
    this(RestAuthenticationValidator::requestToken);
  }

  RestAuthenticationValidator(TokenRequest tokenRequest) {
    this.tokenRequest = tokenRequest;
  }

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
      case OAuthAuthentication oauth -> requestTokenFor(oauth);
      case OAuthRefreshTokenAuthentication ignored ->
          ConfigurationValidationResult
              .unsupported(); // Refresh-token rotation can invalidate the token just used
    };
  }

  private ConfigurationValidationResult requestTokenFor(Authentication authentication) {
    try {
      tokenRequest.run(authentication);
      return ConfigurationValidationResult.success();
    } catch (ConnectorException e) {
      LOG.debug("Token request failed for a REST authentication credential", e);
      return isCredentialRejected(e)
          ? ConfigurationValidationResult.failure(ErrorCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE)
          : ConfigurationValidationResult.failure(ErrorCode.ERROR, GENERIC_MESSAGE);
    } catch (Exception e) {
      LOG.debug("REST authentication credential validation failed", e);
      return ConfigurationValidationResult.failure(ErrorCode.ERROR, GENERIC_MESSAGE);
    }
  }

  private static boolean isCredentialRejected(ConnectorException e) {
    return contains(UNAUTHORIZED_ERROR_CODES, e.getErrorCode())
        || contains(UNAUTHORIZED_OAUTH_ERRORS, oauthErrorOf(e));
  }

  /** Null-safe: both lookups are legitimately absent and {@code Set.of} rejects null. */
  private static boolean contains(Set<String> values, String candidate) {
    return candidate != null && values.contains(candidate);
  }

  /** The OAuth {@code error} identifier from the token endpoint's response body, or null. */
  private static String oauthErrorOf(ConnectorException e) {
    Map<String, Object> variables = e.getErrorVariables();
    if (variables == null || !(variables.get("response") instanceof Map<?, ?> response)) {
      return null;
    }
    if (!(response.get("body") instanceof Map<?, ?> body)) {
      return null;
    }
    return body.get(OAuthConstants.ERROR) instanceof String error ? error : null;
  }

  /** Requests a token as execution does, bypassing the shared cache so no cached token passes. */
  private static void requestToken(Authentication authentication) {
    OAuthService oAuthService = new OAuthService();
    HttpAuthentication mapped = AuthenticationMapper.map(authentication);
    switch (mapped) {
      case io.camunda.connector.http.client.model.auth.OAuthAuthentication oauth ->
          new CustomApacheHttpClient()
              .execute(
                  oAuthService.createOAuthRequestFrom(oauth),
                  oAuthService::extractTokenFromResponse);
      case io.camunda.connector.http.client.model.auth.OAuthRefreshTokenAuthentication oauth ->
          new CustomApacheHttpClient()
              .execute(
                  oAuthService.createOAuthRefreshTokenRequestFrom(oauth),
                  oAuthService::extractTokenFromRefreshTokenResponse);
      default ->
          throw new IllegalStateException(
              "Not an OAuth authentication: " + mapped.getClass().getName());
    }
  }
}
