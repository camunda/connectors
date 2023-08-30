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
package io.camunda.connector.http.base.services;

import static org.apache.http.entity.ContentType.APPLICATION_FORM_URLENCODED;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.UrlEncodedContent;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.gson.GsonFactory;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.http.base.auth.OAuthAuthentication;
import io.camunda.connector.http.base.constants.Constants;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.base.model.HttpRequestBuilder;
import jakarta.validation.ValidationException;
import java.io.IOException;
import java.util.Optional;
import org.apache.commons.text.StringEscapeUtils;

public class HttpRequestMapper {

  private HttpRequestMapper() {}

  public static HttpRequest toOAuthHttpRequest(
      final HttpRequestFactory requestFactory, final HttpCommonRequest request) throws IOException {

    OAuthAuthentication authentication = (OAuthAuthentication) request.getAuthentication();

    HttpHeaders headers = new HttpHeaders();
    if (authentication.getClientAuthentication().equals(Constants.BASIC_AUTH_HEADER)) {
      headers.setBasicAuthentication(
          authentication.getClientId(), authentication.getClientSecret());
    }
    headers.setContentType(Constants.APPLICATION_X_WWW_FORM_URLENCODED);

    return new HttpRequestBuilder()
        .method(HttpMethod.post)
        .genericUrl(new GenericUrl(authentication.getOauthTokenEndpoint()))
        .content(new UrlEncodedContent(authentication.getDataForAuthRequestBody()))
        .headers(headers)
        .connectionTimeoutInSeconds(request.getConnectionTimeoutInSeconds())
        .followRedirects(false)
        .build(requestFactory);
  }

  public static HttpRequest toHttpRequest(
      final HttpRequestFactory requestFactory, final HttpCommonRequest request) throws IOException {
    return toHttpRequest(requestFactory, request, null);
  }

  public static HttpRequest toHttpRequest(
      final HttpRequestFactory requestFactory,
      final HttpCommonRequest request,
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
    if (request.hasBody() && request.getBody() instanceof String) {
      String unescapeBody = StringEscapeUtils.unescapeJson((String) request.getBody());
      request.setBody(unescapeBody);
    }

    HttpContent content = null;
    if (request.getMethod().supportsBody) {
      if (APPLICATION_FORM_URLENCODED.getMimeType().equalsIgnoreCase(headers.getContentType())) {
        content = new UrlEncodedContent(request.getBody());
      } else {
        content =
            request.hasBody() ? new JsonHttpContent(new GsonFactory(), request.getBody()) : null;
      }
    }

    return new HttpRequestBuilder()
        .method(request.getMethod())
        .genericUrl(genericUrl)
        .content(content)
        .headers(headers)
        .connectionTimeoutInSeconds(request.getConnectionTimeoutInSeconds())
        .followRedirects(false)
        .build(requestFactory);
  }

  private static HttpHeaders createHeaders(final HttpCommonRequest request, String bearerToken) {
    final HttpHeaders httpHeaders = new HttpHeaders();
    if (request.hasBody()) {
      // set 'application/json' contentType if content type not exist in request
      boolean isContentTypeNotSet =
          Optional.ofNullable(request.getHeaders())
              .map(
                  headers ->
                      headers.entrySet().stream()
                          .noneMatch(header -> header.getKey().equalsIgnoreCase("content-type")))
              .orElse(true);

      if (isContentTypeNotSet) {
        httpHeaders.setContentType(APPLICATION_JSON.getMimeType());
      }
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
