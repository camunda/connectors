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
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.HttpMethod;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Presents the credential to an endpoint and reports whether it was refused. A static secret
 * (basic, bearer, API key) goes to {@link RestAuthenticationConfiguration#url()}, which the
 * configuration makes mandatory for exactly those types; an OAuth client-credentials grant asks its
 * own token endpoint for a token. Only the refresh-token grant is left unchecked, since a check
 * would consume a token the provider may rotate (RFC 6749 §6).
 *
 * <p>What counts as a refusal differs between the two, because the evidence does: a token endpoint
 * refuses under a defined contract, while an arbitrary resource URL is answering a request derived
 * from the credential rather than one a task makes — see {@code callEndpoint} below. Anything short
 * of a stated refusal is {@link ConfigurationValidationResult#unsupported() unsupported} rather
 * than a failure, so a credential that works is never condemned by a check that could not reach a
 * verdict.
 */
public class RestAuthenticationValidator
    implements ConfigurationValidator<RestAuthenticationConfiguration> {

  private static final Logger LOG = LoggerFactory.getLogger(RestAuthenticationValidator.class);

  static final String MISSING_AUTH_MESSAGE =
      "A credential must specify an authentication mechanism other than 'None'.";
  static final String UNAUTHORIZED_MESSAGE = "The endpoint rejected the credential (unauthorized).";
  static final String GENERIC_MESSAGE =
      "The REST authentication credential could not be validated.";

  private static final Set<String> UNAUTHORIZED_ERROR_CODES =
      Set.of("401", "403", "OAUTH_REFRESH_TOKEN_EXPIRED", "OAUTH_INTERACTION_REQUIRED");

  private static final Set<String> UNAUTHORIZED_OAUTH_ERRORS =
      Set.of("invalid_client", "invalid_grant", "unauthorized_client");

  @Override
  public ConfigurationValidationResult validate(RestAuthenticationConfiguration configuration) {
    // Fully enumerated rather than defaulted: the exhaustiveness check then turns a newly added
    // authentication variant into a build error instead of a silently unvalidated credential.
    return switch (configuration.authentication()) {
      case null ->
          ConfigurationValidationResult.failure(ErrorCode.INVALID_INPUT, MISSING_AUTH_MESSAGE);
      case NoAuthentication ignored ->
          ConfigurationValidationResult.failure(ErrorCode.INVALID_INPUT, MISSING_AUTH_MESSAGE);
      case BasicAuthentication ignored -> callEndpoint(configuration);
      case BearerAuthentication ignored -> callEndpoint(configuration);
      case ApiKeyAuthentication ignored -> callEndpoint(configuration);
      case OAuthAuthentication oauth -> requestToken(oauth);
      case OAuthRefreshTokenAuthentication ignored -> ConfigurationValidationResult.unsupported();
    };
  }

  /**
   * A bare {@code GET} on the bound URL, which is the only request that can be derived from the
   * credential: the configuration carries no method, and the URL is a default a task may override
   * ({@code HttpCommonRequest#url}). So the endpoint is answering a request no task necessarily
   * makes, and only a 401 — the status RFC 9110 reserves for a missing or invalid credential — is
   * it stating that this credential was refused. Every other answer is reported as no verdict — a
   * 403 (a token scoped elsewhere, or a WAF), a 404 or 405 (the bare GET, not the secret), a
   * redirect, an unreachable host: reporting any of them as a failure would condemn a credential
   * that works.
   */
  private static ConfigurationValidationResult callEndpoint(
      RestAuthenticationConfiguration configuration) {
    var request = new HttpClientRequest();
    request.setMethod(HttpMethod.GET);
    request.setUrl(configuration.url());
    request.setAuthentication(AuthenticationMapper.map(configuration.authentication()));
    try {
      // Only 4xx and above are thrown, and redirects are not followed, so a 3xx lands here.
      var response = new CustomApacheHttpClient().execute(request, ignored -> null);
      return response.status() < 300
          ? ConfigurationValidationResult.success()
          : ConfigurationValidationResult.unsupported();
    } catch (Exception e) {
      logFailure(e);
      return e instanceof ConnectorException connectorException
              && "401".equals(connectorException.getErrorCode())
          ? ConfigurationValidationResult.failure(ErrorCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE)
          : ConfigurationValidationResult.unsupported();
    }
  }

  /**
   * Unlike an arbitrary resource URL, a token endpoint has a contract (RFC 6749 §5.2) under which
   * its refusals do speak about the credential, so {@link #classifyFailure(Exception)} keeps its
   * verdict on all of them.
   */
  private static ConfigurationValidationResult requestToken(OAuthAuthentication authentication) {
    var oAuthService = new OAuthService();
    var mapped =
        (io.camunda.connector.http.client.model.auth.OAuthAuthentication)
            AuthenticationMapper.map(authentication);
    try {
      var response =
          new CustomApacheHttpClient()
              .execute(
                  oAuthService.createOAuthRequestFrom(mapped),
                  oAuthService::extractTokenFromResponse);
      return response.status() < 300
          ? ConfigurationValidationResult.success()
          : ConfigurationValidationResult.failure(ErrorCode.ERROR, GENERIC_MESSAGE);
    } catch (Exception e) {
      logFailure(e);
      return classifyFailure(e);
    }
  }

  private static void logFailure(Exception e) {
    LOG.debug(
        "Validation request failed for a REST authentication credential (type {}, code {})",
        e.getClass().getName(),
        e instanceof ConnectorException connectorException
            ? connectorException.getErrorCode()
            : "n/a");
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
