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
package io.camunda.connector.http;

import static org.apache.http.entity.ContentType.APPLICATION_JSON;

import com.google.api.client.http.AbstractHttpContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.UrlEncodedContent;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.gson.Gson;
import io.camunda.connector.common.auth.OAuthAuthentication;
import io.camunda.connector.common.constants.Constants;
import io.camunda.connector.common.model.CommonRequest;
import io.camunda.connector.http.auth.ProxyOAuthHelper;
import io.camunda.connector.http.components.GsonComponentSupplier;
import io.camunda.connector.http.model.HttpJsonRequest;
import io.camunda.connector.http.model.HttpRequestBuilder;
import io.camunda.connector.impl.ConnectorInputException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.validation.ValidationException;

public class HttpRequestMapper {

  private static final Gson gson = GsonComponentSupplier.gsonInstance();

  private HttpRequestMapper() {}

  public static HttpRequest toRequestViaProxy(
      final HttpRequestFactory requestFactory,
      final HttpJsonRequest request,
      final String proxyFunctionUrl)
      throws IOException {
    // Using the JsonHttpContent cannot work with an element on the root content,
    // hence write it ourselves:
    final String contentAsJson = gson.toJson(request);
    HttpContent content =
        new AbstractHttpContent(Constants.APPLICATION_JSON_CHARSET_UTF_8) {
          public void writeTo(OutputStream outputStream) throws IOException {
            outputStream.write(contentAsJson.getBytes(StandardCharsets.UTF_8));
          }
        };

    HttpRequest httpRequest =
        new HttpRequestBuilder()
            .method(Constants.POST)
            .genericUrl(new GenericUrl(proxyFunctionUrl))
            .content(content)
            .connectionTimeoutInSeconds(request.getConnectionTimeoutInSeconds())
            .followRedirects(false)
            .build(requestFactory);

    ProxyOAuthHelper.addOauthHeaders(
        httpRequest, ProxyOAuthHelper.initializeCredentials(proxyFunctionUrl));
    return httpRequest;
  }

  public static HttpRequest toOAuthHttpRequest(
      final HttpRequestFactory requestFactory, final HttpJsonRequest request) throws IOException {

    OAuthAuthentication authentication = (OAuthAuthentication) request.getAuthentication();

    HttpHeaders headers = new HttpHeaders();
    if (authentication.getClientAuthentication().equals(Constants.BASIC_AUTH_HEADER)) {
      headers.setBasicAuthentication(
          authentication.getClientId(), authentication.getClientSecret());
    }
    headers.setContentType(Constants.APPLICATION_X_WWW_FORM_URLENCODED);

    return new HttpRequestBuilder()
        .method(Constants.POST)
        .genericUrl(new GenericUrl(authentication.getOauthTokenEndpoint()))
        .content(new UrlEncodedContent(getDataForAuthRequestBody(authentication)))
        .headers(headers)
        .connectionTimeoutInSeconds(request.getConnectionTimeoutInSeconds())
        .followRedirects(false)
        .build(requestFactory);
  }

  public static Map<String, String> getDataForAuthRequestBody(OAuthAuthentication authentication) {
    Map<String, String> data = new HashMap<>();
    data.put(Constants.GRANT_TYPE, authentication.getGrantType());
    data.put(Constants.AUDIENCE, authentication.getAudience());
    data.put(Constants.SCOPE, authentication.getScopes());

    if (Constants.CREDENTIALS_BODY.equals(authentication.getClientAuthentication())) {
      data.put(Constants.CLIENT_ID, authentication.getClientId());
      data.put(Constants.CLIENT_SECRET, authentication.getClientSecret());
    }
    return data;
  }

  public static HttpRequest toHttpRequest(
      final HttpRequestFactory requestFactory, final CommonRequest request) throws IOException {
    return toHttpRequest(requestFactory, request, null);
  }

  public static HttpRequest toHttpRequest(
      final HttpRequestFactory requestFactory,
      final CommonRequest request,
      final String bearerToken)
      throws IOException {
    // TODO: add more holistic solution
    if (request.getUrl().contains("computeMetadata")) {
      throw new ConnectorInputException(new ValidationException("The provided URL is not allowed"));
    }
    final GenericUrl genericUrl = new GenericUrl(request.getUrl());
    final HttpHeaders headers = createHeaders(request, bearerToken);

    if (request.hasQueryParameters()) {
      genericUrl.putAll(request.getQueryParameters());
    }

    return new HttpRequestBuilder()
        .method(request.getMethod().toUpperCase())
        .genericUrl(genericUrl)
        .content(
            request.hasBody()
                ? new JsonHttpContent(
                    GsonComponentSupplier.gsonFactoryInstance(), request.getBody())
                : null)
        .headers(headers)
        .connectionTimeoutInSeconds(request.getConnectionTimeoutInSeconds())
        .followRedirects(false)
        .build(requestFactory);
  }

  private static HttpHeaders createHeaders(final CommonRequest request, String bearerToken) {
    final HttpHeaders httpHeaders = new HttpHeaders();
    if (request.hasBody()) {
      httpHeaders.setContentType(APPLICATION_JSON.getMimeType());
    }
    if (request.hasAuthentication()) {
      if (bearerToken != null && !bearerToken.isEmpty()) {
        httpHeaders.setAuthorization("Bearer " + bearerToken);
      }
      request.getAuthentication().setHeaders(httpHeaders);
    }
    if (request.hasHeaders()) {
      httpHeaders.putAll(request.getHeaders());
    }
    return httpHeaders;
  }
}
