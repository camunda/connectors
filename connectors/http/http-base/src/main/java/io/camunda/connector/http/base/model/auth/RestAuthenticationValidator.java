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
import io.camunda.connector.http.client.mapper.ResponseMapper;
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

  private static ConfigurationValidationResult callEndpoint(
      RestAuthenticationConfiguration configuration) {
    var request = new HttpClientRequest();
    request.setMethod(HttpMethod.GET);
    request.setUrl(configuration.url());
    request.setAuthentication(AuthenticationMapper.map(configuration.authentication()));
    return attempt(request, response -> null);
  }

  private static ConfigurationValidationResult requestToken(OAuthAuthentication authentication) {
    var oAuthService = new OAuthService();
    var mapped =
        (io.camunda.connector.http.client.model.auth.OAuthAuthentication)
            AuthenticationMapper.map(authentication);
    return attempt(
        oAuthService.createOAuthRequestFrom(mapped), oAuthService::extractTokenFromResponse);
  }

  private static <T> ConfigurationValidationResult attempt(
      HttpClientRequest request, ResponseMapper<T> responseMapper) {
    try {
      var response = new CustomApacheHttpClient().execute(request, responseMapper);
      // Only 4xx and above are thrown, and redirects are not followed, so a 3xx lands here: an
      // endpoint that answers an unaccepted credential by redirecting to a login page has not
      // accepted it.
      if (response.status() >= 300) {
        LOG.debug("A REST authentication credential was answered with {}", response.status());
        return ConfigurationValidationResult.failure(ErrorCode.ERROR, GENERIC_MESSAGE);
      }
      return ConfigurationValidationResult.success();
    } catch (Exception e) {
      LOG.debug(
          "Validation request failed for a REST authentication credential (type {}, code {})",
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
