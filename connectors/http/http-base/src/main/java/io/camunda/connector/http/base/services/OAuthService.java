/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.http.base.services;

import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.auth.OAuthAuthentication;
import io.camunda.connector.http.base.constants.Constants;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.base.utils.Base64Helper;
import io.camunda.connector.http.base.utils.JsonHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;

public class OAuthService {

  /**
   * Converts a {@link HttpCommonRequest} to a request that can be used to fetch an OAuth token.
   * This method will create a new request with the OAuth token endpoint as the URL, the
   * authentication data as the body, and the client ID and client secret as the basic
   * authentication header (if the client authentication is set to {@link
   * Constants#BASIC_AUTH_HEADER}), or as client credentials in the request body (if the client
   * authentication is set to {@link Constants#CREDENTIALS_BODY}).
   *
   * @param request the request to convert
   * @return a new request that can be used to fetch an OAuth token
   * @see OAuthAuthentication
   */
  public HttpCommonRequest createOAuthRequestFrom(HttpCommonRequest request) {
    OAuthAuthentication authentication = (OAuthAuthentication) request.getAuthentication();
    HttpCommonRequest oauthRequest = new HttpCommonRequest();
    Map<String, String> headers = new HashMap<>();

    headers.put(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED.getMimeType());

    oauthRequest.setMethod(HttpMethod.POST);
    oauthRequest.setUrl(authentication.oauthTokenEndpoint());
    Map<String, String> body = authentication.getDataForAuthRequestBody();

    // Depending on the client authentication, add the client ID and client secret to the request
    // either as basic authentication header or as client credentials in the request body
    addCredentials(body, headers, authentication);

    oauthRequest.setBody(body);
    // copy timeouts from request
    oauthRequest.setConnectionTimeoutInSeconds(request.getConnectionTimeoutInSeconds());
    oauthRequest.setReadTimeoutInSeconds(request.getReadTimeoutInSeconds());

    oauthRequest.setHeaders(headers);

    return oauthRequest;
  }

  public String extractTokenFromResponse(Object body) {
    return Optional.ofNullable(
            JsonHelper.getAsJsonElement(body, ConnectorsObjectMapperSupplier.DEFAULT_MAPPER))
        .map(jsonNode -> jsonNode.findValue(Constants.ACCESS_TOKEN).asText())
        .orElse(null);
  }

  private void addCredentials(
      Map<String, String> body, Map<String, String> headers, OAuthAuthentication authentication) {
    switch (authentication.clientAuthentication()) {
      case Constants.BASIC_AUTH_HEADER ->
          headers.put(
              HttpHeaders.AUTHORIZATION,
              Base64Helper.buildBasicAuthenticationHeader(
                  authentication.clientId(), authentication.clientSecret()));
      case Constants.CREDENTIALS_BODY -> {
        body.put(Constants.CLIENT_ID, authentication.clientId());
        body.put(Constants.CLIENT_SECRET, authentication.clientSecret());
      }
      default ->
          throw new IllegalArgumentException(
              "Unsupported client authentication method: "
                  + authentication.clientAuthentication()
                  + ". Please use either 'basicAuthHeader' or 'credentialsBody'.");
    }
  }
}
