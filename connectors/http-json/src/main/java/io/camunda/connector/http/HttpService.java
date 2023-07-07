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

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.gson.Gson;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.common.auth.CustomAuthentication;
import io.camunda.connector.common.auth.OAuthAuthentication;
import io.camunda.connector.common.services.AuthenticationService;
import io.camunda.connector.common.services.HTTPProxyService;
import io.camunda.connector.common.services.HTTPService;
import io.camunda.connector.http.model.HttpJsonRequest;
import io.camunda.connector.http.model.HttpJsonResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpService {
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpService.class);

  private final Gson gson;
  private final HttpRequestFactory requestFactory;
  private final String proxyFunctionUrl;

  public HttpService(
      final Gson gson, final HttpRequestFactory requestFactory, final String proxyFunctionUrl) {
    this.gson = gson;
    this.requestFactory = requestFactory;
    this.proxyFunctionUrl = proxyFunctionUrl;
  }

  public Object executeConnectorRequest(final HttpJsonRequest request)
      throws IOException, InstantiationException, IllegalAccessException {
    return proxyFunctionUrl == null
        ? executeRequestDirectly(request)
        : executeRequestViaProxy(request);
  }

  private HttpJsonResult executeRequestDirectly(HttpJsonRequest request)
      throws IOException, InstantiationException, IllegalAccessException {
    String bearerToken = null;
    HTTPService httpService = new HTTPService(gson);
    AuthenticationService authService = new AuthenticationService(gson, requestFactory);
    if (request.getAuthentication() != null) {
      if (request.getAuthentication() instanceof OAuthAuthentication) {
        bearerToken = getTokenFromOAuthRequest(request, httpService, authService);
      } else if (request.getAuthentication() instanceof CustomAuthentication authentication) {
        final var httpRequest =
            HttpRequestMapper.toHttpRequest(requestFactory, authentication.getRequest());
        HttpResponse httpResponse = httpService.executeHttpRequest(httpRequest);
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
    HttpRequest httpRequest = HttpRequestMapper.toHttpRequest(requestFactory, request, bearerToken);
    HttpResponse httpResponse = httpService.executeHttpRequest(httpRequest, false);
    return httpService.toHttpResponse(httpResponse, HttpJsonResult.class);
  }

  private String getTokenFromOAuthRequest(
      final HttpJsonRequest connectorRequest,
      final HTTPService httpService,
      final AuthenticationService authService)
      throws IOException {
    final HttpRequest oauthRequest = authService.createOAuthRequest(connectorRequest);
    final HttpResponse oauthResponse = httpService.executeHttpRequest(oauthRequest);
    return authService.extractOAuthAccessToken(oauthResponse);
  }

  private HttpJsonResult executeRequestViaProxy(HttpJsonRequest request) throws IOException {
    HttpRequest httpRequest =
        HTTPProxyService.toRequestViaProxy(gson, requestFactory, request, proxyFunctionUrl);

    HTTPService httpService = new HTTPService(gson);

    HttpResponse httpResponse = httpService.executeHttpRequest(httpRequest, true);

    try (InputStream responseContentStream = httpResponse.getContent();
        Reader reader = new InputStreamReader(responseContentStream)) {
      final HttpJsonResult jsonResult = gson.fromJson(reader, HttpJsonResult.class);
      LOGGER.debug("Proxy returned result: " + jsonResult);
      return jsonResult;
    } catch (final Exception e) {
      LOGGER.debug("Failed to parse external response: {}", httpResponse, e);
      throw new ConnectorException("Failed to parse result: " + e.getMessage(), e);
    }
  }
}
