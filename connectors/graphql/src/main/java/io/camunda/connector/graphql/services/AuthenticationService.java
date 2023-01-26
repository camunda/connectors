/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql.services;

import static io.camunda.connector.graphql.utils.Timeout.setTimeout;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.UrlEncodedContent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.camunda.connector.graphql.auth.OAuthAuthentication;
import io.camunda.connector.graphql.constants.Constants;
import io.camunda.connector.graphql.model.GraphQLRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthenticationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationService.class);

  private static AuthenticationService instance;

  private final Gson gson;
  private final HttpRequestFactory requestFactory;

  private AuthenticationService(final Gson gson, final HttpRequestFactory requestFactory) {
    this.gson = gson;
    this.requestFactory = requestFactory;
  }

  public static AuthenticationService getInstance(
      final Gson gson, final HttpRequestFactory requestFactory) {
    if (instance == null) {
      instance = new AuthenticationService(gson, requestFactory);
    }
    return instance;
  }

  public String extractAccessToken(HttpResponse oauthResponse) throws IOException {
    String oauthResponseStr = oauthResponse.parseAsString();
    if (oauthResponseStr != null && !oauthResponseStr.isEmpty()) {
      JsonObject jsonObject = gson.fromJson(oauthResponseStr, JsonObject.class);
      if (jsonObject.get(Constants.ACCESS_TOKEN) != null) {
        return jsonObject.get(Constants.ACCESS_TOKEN).getAsString();
      }
    }
    return null;
  }

  public HttpRequest createOAuthRequest(GraphQLRequest request) throws IOException {
    OAuthAuthentication authentication = (OAuthAuthentication) request.getAuthentication();

    final GenericUrl genericUrl = new GenericUrl(authentication.getOauthTokenEndpoint());
    Map<String, String> data = getDataForAuthRequestBody(authentication);
    HttpContent content = new UrlEncodedContent(data);
    final String method = Constants.POST;
    final var httpRequest = requestFactory.buildRequest(method, genericUrl, content);
    httpRequest.setFollowRedirects(false);
    setTimeout(request, httpRequest);
    HttpHeaders headers = new HttpHeaders();

    if (authentication.getClientAuthentication().equals(Constants.BASIC_AUTH_HEADER)) {
      headers.setBasicAuthentication(
          authentication.getClientId(), authentication.getClientSecret());
    }
    headers.setContentType(Constants.APPLICATION_X_WWW_FORM_URLENCODED);
    httpRequest.setHeaders(headers);
    return httpRequest;
  }

  private static Map<String, String> getDataForAuthRequestBody(OAuthAuthentication authentication) {
    Map<String, String> data = new HashMap<>();
    data.put(Constants.GRANT_TYPE, authentication.getGrantType());
    data.put(Constants.AUDIENCE, authentication.getAudience());
    data.put(Constants.SCOPE, authentication.getScopes());

    if (authentication.getClientAuthentication().equals(Constants.CREDENTIALS_BODY)) {
      data.put(Constants.CLIENT_ID, authentication.getClientId());
      data.put(Constants.CLIENT_SECRET, authentication.getClientSecret());
    }
    return data;
  }
}
