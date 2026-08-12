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
 * Validates a {@link RestAuthenticationConfiguration} out-of-band, and is therefore shared by every
 * connector that binds it — REST, GraphQL, HTTP polling.
 *
 * <p>How much can be checked depends on the authentication variant, because the credential does not
 * name the API it is meant for:
 *
 * <ul>
 *   <li>Both OAuth 2.0 variants carry their own token endpoint, so the check is a real one: request
 *       a token exactly as connector execution would, and require an access token back.
 *   <li>Basic, bearer and API key carry a secret and nothing to present it to, so there is no
 *       out-of-band check to run and the result is {@link
 *       ConfigurationValidationResult#unsupported() unsupported} — reporting success would confirm
 *       a secret nobody verified.
 *   <li>No authentication is usable by definition.
 * </ul>
 *
 * <p>Messages returned to the caller are static and value-free: a token endpoint's error body is
 * echoed into the exception message and can carry the client id, the endpoint, or provider-side
 * detail, so it is never surfaced. The full exception is available at {@code DEBUG} for operators
 * who need to diagnose a failure — enabling that level is an explicit, deployment-level decision to
 * accept those details in the logs.
 */
public class RestAuthenticationValidator
    implements ConfigurationValidator<RestAuthenticationConfiguration> {

  private static final Logger LOG = LoggerFactory.getLogger(RestAuthenticationValidator.class);

  static final String MISSING_AUTH_MESSAGE = "Authentication is required.";
  static final String UNAUTHORIZED_MESSAGE =
      "The token endpoint rejected the credential (unauthorized).";
  static final String GENERIC_MESSAGE =
      "The REST authentication credential could not be validated.";

  /**
   * Error codes that mean the credential itself was rejected, rather than the token endpoint being
   * unreachable or broken.
   *
   * <p>{@code 401} and {@code 403} are the HTTP statuses; the {@code OAUTH_*} codes are raised by
   * {@link OAuthService} when a {@code 200} response carries an OAuth error instead of a token.
   */
  private static final Set<String> UNAUTHORIZED_ERROR_CODES =
      Set.of("401", "403", "OAUTH_REFRESH_TOKEN_EXPIRED", "OAUTH_INTERACTION_REQUIRED");

  /**
   * OAuth error identifiers (RFC 6749 §5.2) that mean the credential itself was rejected. Needed on
   * top of the status codes above because a token endpoint is free to report a rejected client
   * secret or an expired refresh token as {@code 400}, and commonly does.
   */
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
    // The only guard against a missing authentication: the configuration record carries @Valid but
    // no @NotNull, so nothing upstream rejects it, and an unauthenticated credential is not a
    // credential.
    if (configuration.authentication() == null) {
      return ConfigurationValidationResult.failure(ErrorCode.INVALID_INPUT, MISSING_AUTH_MESSAGE);
    }
    return switch (configuration.authentication()) {
      case NoAuthentication -> ConfigurationValidationResult.success();
      case BasicAuthentication _ -> ConfigurationValidationResult.unsupported();
      case BearerAuthentication _ -> ConfigurationValidationResult.unsupported();
      case ApiKeyAuthentication _ -> ConfigurationValidationResult.unsupported();
      case OAuthAuthentication oauth -> requestTokenFor(oauth);
      case OAuthRefreshTokenAuthentication oauth -> requestTokenFor(oauth);
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

  /** Both lookups are legitimately absent, and {@code Set.of} rejects a null argument. */
  private static boolean contains(Set<String> values, String candidate) {
    return candidate != null && values.contains(candidate);
  }

  /**
   * The OAuth {@code error} identifier from the token endpoint's response body, or {@code null} if
   * the response carried none. The body travels on the exception's error variables under the same
   * {@code response.body} shape a BPMN error handler sees.
   */
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

  /**
   * Requests a token the same way connector execution does, so a credential that validates here
   * cannot fail there for a reason validation never exercised.
   *
   * <p>The shared OAuth token cache is deliberately bypassed: a token cached by an earlier
   * execution would make any credential validate, and a token fetched here would be attributed to a
   * process that never ran.
   */
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
