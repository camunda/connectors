/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.automationanywhere.model.TokenResponse;
import io.camunda.connector.http.base.HttpService;
import io.camunda.connector.http.base.model.HttpCommonResult;

/**
 * Interface representing a provider for obtaining authentication tokens. This is part of the
 * authentication system for the Camunda Automation Anywhere Connector. Implementations of this
 * interface handle different types of authentication and obtain tokens accordingly.
 *
 * <p>It is a sealed interface, meaning only specific, allowed classes can implement it.
 */
public sealed interface AuthenticationProvider
    permits TokenBasedAuthProvider, PasswordBasedAuthProvider, ApiKeyAuthProvider {

  String AUTHENTICATION_URL_PATTERN = "%s/v1/authentication";

  /**
   * Obtains an authentication token using the specified HttpService and ObjectMapper.
   *
   * @param httpService The service used for executing HTTP requests.
   * @param objectMapper The ObjectMapper used to parse the authentication response and fetch the
   *     token.
   * @return A string representing the obtained authentication token.
   * @throws Exception if there is an error during the token retrieval process.
   */
  String obtainToken(final HttpService httpService, final ObjectMapper objectMapper)
      throws Exception;

  /**
   * Constructs the authentication request URL using the given control room URL.
   *
   * @param controlRoomUrl The base URL of the control room.
   * @return The full URL for authentication requests.
   */
  default String getAuthenticationRequestUrl(final String controlRoomUrl) {
    return String.format(AUTHENTICATION_URL_PATTERN, controlRoomUrl);
  }

  /**
   * Extracts the authentication token from the HTTP response. This method uses the provided
   * ObjectMapper to parse the response body, converting it into a TokenResponse object, and then
   * retrieves the token from it.
   *
   * <p>The method assumes that the response body contains a JSON representation of the
   * TokenResponse, with a structure corresponding to the TokenResponse record (i.e., a single field
   * named 'token').
   *
   * @param result The HTTP result containing the response body, typically a response from an
   *     authentication request.
   * @param objectMapper The ObjectMapper used to parse the response body into a TokenResponse
   *     object.
   * @return The extracted token as a String.
   */
  default String fetchToken(final HttpCommonResult result, final ObjectMapper objectMapper) {
    return objectMapper.convertValue(result.body(), TokenResponse.class).token();
  }
}
