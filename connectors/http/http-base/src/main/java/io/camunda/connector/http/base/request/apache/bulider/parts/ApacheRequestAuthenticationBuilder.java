/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.http.base.request.apache.bulider.parts;

import static org.apache.hc.core5.http.HttpHeaders.AUTHORIZATION;

import io.camunda.connector.http.base.auth.ApiKeyAuthentication;
import io.camunda.connector.http.base.auth.BasicAuthentication;
import io.camunda.connector.http.base.auth.BearerAuthentication;
import io.camunda.connector.http.base.auth.NoAuthentication;
import io.camunda.connector.http.base.auth.OAuthAuthentication;
import io.camunda.connector.http.base.components.apache.CustomApacheHttpClient;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.services.OAuthService;
import io.camunda.connector.http.base.utils.Base64Helper;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

public class ApacheRequestAuthenticationBuilder implements ApacheRequestPartBuilder {

  private static final String BEARER = "Bearer %s";
  private final OAuthService oAuthService = new OAuthService();

  @Override
  public void build(ClassicRequestBuilder builder, HttpCommonRequest request) throws Exception {
    if (request.hasAuthentication()) {
      switch (request.getAuthentication()) {
        case NoAuthentication ignored -> {}
        case BasicAuthentication auth ->
            builder.addHeader(
                AUTHORIZATION,
                Base64Helper.buildBasicAuthenticationHeader(auth.username(), auth.password()));
        case OAuthAuthentication ignored -> {
          String token = fetchOAuthToken(request);
          builder.addHeader(AUTHORIZATION, String.format(BEARER, token));
        }
        case BearerAuthentication auth ->
            builder.addHeader(AUTHORIZATION, String.format(BEARER, auth.token()));
        case ApiKeyAuthentication auth -> {
          if (auth.isQueryLocationApiKeyAuthentication()) {
            builder.addParameter(auth.name(), auth.value());
          } else {
            builder.addHeader(auth.name(), auth.value());
          }
        }
      }
    }
  }

  private String fetchOAuthToken(HttpCommonRequest request) throws Exception {
    HttpCommonRequest oAuthRequest = oAuthService.createOAuthRequestFrom(request);
    HttpCommonResult response = CustomApacheHttpClient.getDefault().execute(oAuthRequest);
    return oAuthService.extractTokenFromResponse(response.getBody());
  }
}
