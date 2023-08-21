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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.base.auth.CustomAuthentication;
import io.camunda.connector.http.base.auth.OAuthAuthentication;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpService {
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpService.class);

  private final ObjectMapper objectMapper;
  private final HttpRequestFactory requestFactory;
  private final String proxyFunctionUrl;

  public HttpService(
      final ObjectMapper objectMapper,
      final HttpRequestFactory requestFactory,
      final String proxyFunctionUrl) {
    this.objectMapper = objectMapper;
    this.requestFactory = requestFactory;
    this.proxyFunctionUrl = proxyFunctionUrl;
  }

  public HttpCommonResult executeConnectorRequest(final HttpCommonRequest request)
      throws IOException, InstantiationException, IllegalAccessException {
    return proxyFunctionUrl == null
        ? executeRequestDirectly(request)
        : executeRequestViaProxy(request);
  }

  private HttpCommonResult executeRequestDirectly(HttpCommonRequest request)
      throws IOException, InstantiationException, IllegalAccessException {
    String bearerToken = null;
    HttpInteractionService httpInteractionService = new HttpInteractionService(objectMapper);
    AuthenticationService authService = new AuthenticationService(objectMapper, requestFactory);
    if (request.getAuthentication() != null) {
      if (request.getAuthentication() instanceof OAuthAuthentication) {
        bearerToken = getTokenFromOAuthRequest(request, httpInteractionService, authService);
      } else if (request.getAuthentication() instanceof CustomAuthentication authentication) {
        final var httpRequest =
            HttpRequestMapper.toHttpRequest(requestFactory, authentication.getRequest());
        HttpResponse httpResponse = httpInteractionService.executeHttpRequest(httpRequest);
        if (httpResponse.isSuccessStatusCode()) {
          authService.fillRequestFromCustomAuthResponseData(request, authentication, httpResponse);
        } else {
          throw new RuntimeException(
              "Authenticate is fail; status code : ["
                  + httpResponse.getStatusCode()
                  + "], message : ["
                  + httpResponse.getStatusMessage()
                  + "]");
        }
      }
    }
    com.google.api.client.http.HttpRequest httpRequest =
        HttpRequestMapper.toHttpRequest(requestFactory, request, bearerToken);
    HttpResponse httpResponse = httpInteractionService.executeHttpRequest(httpRequest, false);
    return httpInteractionService.toHttpResponse(httpResponse, HttpCommonResult.class);
  }

  private String getTokenFromOAuthRequest(
      final HttpCommonRequest connectorRequest,
      final HttpInteractionService httpInteractionService,
      final AuthenticationService authService)
      throws IOException {
    final com.google.api.client.http.HttpRequest oauthRequest =
        authService.createOAuthRequest(connectorRequest);
    final HttpResponse oauthResponse = httpInteractionService.executeHttpRequest(oauthRequest);
    return authService.extractOAuthAccessToken(oauthResponse);
  }

  private HttpCommonResult executeRequestViaProxy(HttpCommonRequest request) throws IOException {
    HttpRequest httpRequest =
        HTTPProxyService.toRequestViaProxy(requestFactory, request, proxyFunctionUrl);

    HttpInteractionService httpInteractionService = new HttpInteractionService(objectMapper);

    HttpResponse httpResponse = httpInteractionService.executeHttpRequest(httpRequest, true);

    try (InputStream responseContentStream = httpResponse.getContent();
        Reader reader = new InputStreamReader(responseContentStream)) {
      final HttpCommonResult jsonResult = objectMapper.readValue(reader, HttpCommonResult.class);
      LOGGER.debug("Proxy returned result: " + jsonResult);
      return jsonResult;
    } catch (final Exception e) {
      LOGGER.debug("Failed to parse external response: {}", httpResponse, e);
      throw new ConnectorException("Failed to parse result: " + e.getMessage(), e);
    }
  }
}
