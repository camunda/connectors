/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.common.services;

import static io.camunda.connector.common.utils.Timeout.setTimeout;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.UrlEncodedContent;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.camunda.connector.common.auth.CustomAuthentication;
import io.camunda.connector.common.auth.OAuthAuthentication;
import io.camunda.connector.common.constants.Constants;
import io.camunda.connector.common.model.CommonRequest;
import io.camunda.connector.common.utils.JsonHelper;
import io.camunda.connector.common.utils.ResponseParser;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthenticationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationService.class);

  private final Gson gson;
  private final HttpRequestFactory requestFactory;

  public AuthenticationService(final Gson gson, final HttpRequestFactory requestFactory) {
    this.gson = gson;
    this.requestFactory = requestFactory;
  }

  public String extractOAuthAccessToken(HttpResponse oauthResponse) throws IOException {
    return Optional.ofNullable(JsonHelper.getAsJsonElement(oauthResponse.parseAsString(), gson))
        .map(JsonElement::getAsJsonObject)
        .map(jsonObject -> jsonObject.get(Constants.ACCESS_TOKEN))
        .map(JsonElement::getAsString)
        .orElse(null);
  }

  public void fillRequestFromCustomAuthResponseData(
      final CommonRequest request,
      final CustomAuthentication authentication,
      final HttpResponse httpResponse)
      throws IOException {
    String strResponse = httpResponse.parseAsString();
    Map<String, String> headers =
        ResponseParser.extractPropertiesFromBody(
            authentication.getOutputHeaders(), strResponse, gson);
    if (headers != null) {
      if (!request.hasHeaders()) {
        request.setHeaders(new HashMap<>());
      }
      request.getHeaders().putAll(headers);
    }

    Map<String, String> body =
        ResponseParser.extractPropertiesFromBody(authentication.getOutputBody(), strResponse, gson);
    if (body != null) {
      if (!request.hasBody()) {
        request.setBody(new Object());
      }
      JsonObject requestBody = gson.toJsonTree(request.getBody()).getAsJsonObject();
      // for now, we can add only string property to body, example of this object :
      // "{"key":"value"}" but we can expand this method
      body.forEach(requestBody::addProperty);
      request.setBody(gson.fromJson(gson.toJson(requestBody), Object.class));
    }
  }

  public HttpRequest createOAuthRequest(CommonRequest request) throws IOException {
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
