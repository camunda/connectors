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
import com.google.api.client.http.HttpResponseException;
import com.google.gson.Gson;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.auth.OAuthAuthentication;
import io.camunda.connector.http.model.ErrorResponse;
import io.camunda.connector.http.model.HttpJsonRequest;
import io.camunda.connector.http.model.HttpJsonResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// todo split class, maybe delete, move logic in auth classes or in util classes
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

  public Object executeConnectorRequest(final HttpJsonRequest request) throws IOException {
    return proxyFunctionUrl == null
        ? executeRequestDirectly(request)
        : executeRequestViaProxy(request);
  }

  protected HttpJsonResult executeRequestDirectly(HttpJsonRequest request) throws IOException {
    String bearerToken = null;
    if (request.getAuthentication() != null) {
      if (request.getAuthentication() instanceof OAuthAuthentication) {
        bearerToken = getTokenFromOAuthRequest(request);
      }
    }
    HttpRequest httpRequest = HttpRequestMapper.toHttpRequest(requestFactory, request, bearerToken);

    HttpResponse httpResponse = executeHttpRequest(httpRequest);
    return HttpResponseMapper.toHttpJsonResponse(httpResponse);
  }

  private String getTokenFromOAuthRequest(final HttpJsonRequest request) throws IOException {
    final HttpRequest httpRequest = HttpRequestMapper.toOAuthHttpRequest(requestFactory, request);
    final HttpResponse oauthResponse = executeHttpRequest(httpRequest);
    return ResponseParser.extractOAuthAccessToken(oauthResponse);
  }

  protected HttpResponse executeHttpRequest(HttpRequest externalRequest) throws IOException {
    return executeHttpRequest(externalRequest, false);
  }

  protected HttpResponse executeHttpRequest(HttpRequest externalRequest, boolean isProxyCall)
      throws IOException {
    try {
      return externalRequest.execute();
    } catch (HttpResponseException hrex) {
      var errorCode = String.valueOf(hrex.getStatusCode());
      var errorMessage = hrex.getMessage();
      if (isProxyCall && hrex.getContent() != null) {
        try {
          final var errorContent = gson.fromJson(hrex.getContent(), ErrorResponse.class);
          errorCode = errorContent.getErrorCode();
          errorMessage = errorContent.getError();
        } catch (Exception e) {
          // cannot be loaded as JSON, ignore and use plain message
        }
      }
      throw new ConnectorException(errorCode, errorMessage, hrex);
    }
  }

  protected HttpJsonResult executeRequestViaProxy(HttpJsonRequest request) throws IOException {
    HttpRequest httpRequest =
        HttpRequestMapper.toRequestViaProxy(requestFactory, request, proxyFunctionUrl);

    HttpResponse httpResponse = executeHttpRequest(httpRequest, true);

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
