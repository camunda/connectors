/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

public class AuthenticationService {

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
    Map<String, String> data = authentication.getDataForAuthRequestBody();
    HttpContent content = new UrlEncodedContent(data);
    final String method = Constants.POST;
    final var httpRequest = requestFactory.buildRequest(method, genericUrl, content);
    httpRequest.setFollowRedirects(false);
    setTimeout(request, httpRequest);
    HttpHeaders headers = new HttpHeaders();

    if (Constants.BASIC_AUTH_HEADER.equals(authentication.getClientAuthentication())) {
      headers.setBasicAuthentication(
          authentication.getClientId(), authentication.getClientSecret());
    }
    headers.setContentType(Constants.APPLICATION_X_WWW_FORM_URLENCODED);
    httpRequest.setHeaders(headers);
    return httpRequest;
  }
}
