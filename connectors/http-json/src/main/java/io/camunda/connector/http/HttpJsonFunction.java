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

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.http.auth.OAuthAuthentication;
import io.camunda.connector.http.components.GsonComponentSupplier;
import io.camunda.connector.http.components.HttpTransportComponentSupplier;
import io.camunda.connector.http.constants.Constants;
import io.camunda.connector.http.model.HttpJsonRequest;
import io.camunda.connector.http.model.HttpJsonResult;
import io.camunda.connector.impl.ConnectorInputException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "HTTPJSON",
    inputVariables = {
      "url",
      "method",
      "authentication",
      "headers",
      "queryParameters",
      "connectionTimeoutInSeconds",
      "body"
    },
    type = "io.camunda:http-json:1")
public class HttpJsonFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpJsonFunction.class);
  private final Gson gson;
  private final GsonFactory gsonFactory;
  private final HttpRequestFactory requestFactory;

  public HttpJsonFunction() {
    this(
        GsonComponentSupplier.gsonInstance(),
        HttpTransportComponentSupplier.httpRequestFactoryInstance(),
        GsonComponentSupplier.gsonFactoryInstance());
  }

  public HttpJsonFunction(
      final Gson gson, final HttpRequestFactory requestFactory, final GsonFactory gsonFactory) {
    this.gson = gson;
    this.requestFactory = requestFactory;
    this.gsonFactory = gsonFactory;
  }

  @Override
  public Object execute(final OutboundConnectorContext context) throws IOException {
    final var json = context.getVariables();
    final var request = gson.fromJson(json, HttpJsonRequest.class);

    context.validate(request);
    context.replaceSecrets(request);

    return handleRequest(request);
  }

  protected HttpJsonResult handleRequest(final HttpJsonRequest request) throws IOException {
    String token = null;
    if (request.getAuthentication() != null
        && request.getAuthentication() instanceof OAuthAuthentication) {
      final HttpRequest oauthRequest = createOAuthRequest(request);
      final HttpResponse oauthResponse = sendRequest(oauthRequest);
      token = extractAccessToken(oauthResponse);
    }

    final HttpRequest externalRequest = createRequest(request, token);
    final HttpResponse externalResponse = sendRequest(externalRequest);
    return toHttpJsonResponse(externalResponse);
  }

  private String extractAccessToken(HttpResponse oauthResponse) throws IOException {
    String token = null;
    String oauthResponseStr = oauthResponse.parseAsString();
    if (oauthResponseStr != null && !oauthResponseStr.isEmpty()) {
      JsonObject jsonObject = gson.fromJson(oauthResponseStr, JsonObject.class);
      if (jsonObject.get(Constants.ACCESS_TOKEN) != null) {
        token = jsonObject.get(Constants.ACCESS_TOKEN).toString();
      }
    }
    return token;
  }

  private HttpRequest createOAuthRequest(HttpJsonRequest request) throws IOException {
    OAuthAuthentication authentication = (OAuthAuthentication) request.getAuthentication();
    final var url = authentication.getOauthTokenEndpoint();
    Map<String, String> data = getDataForAuthRequestBody(authentication);
    HttpContent content = new JsonHttpContent(gsonFactory, data);
    final GenericUrl genericUrl = new GenericUrl(url);

    final HttpRequest httpRequest = initRequest(request, url, content, genericUrl);

    if (authentication.getClientAuthentication().equals(Constants.BASIC_AUTH_HEADER)) {
      HttpHeaders headers = new HttpHeaders();
      headers.setBasicAuthentication(
          authentication.getClientId(), authentication.getClientSecret());
      httpRequest.setHeaders(headers);
    }
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

  private HttpRequest initRequest(
      HttpJsonRequest request, String url, HttpContent content, GenericUrl genericUrl)
      throws IOException {
    if (url.contains(Constants.COMPUTE_METADATA)) {
      throw new ConnectorInputException(new ValidationException("The provided URL is not allowed"));
    }

    final var method = request.getMethod().toUpperCase();

    final var httpRequest = requestFactory.buildRequest(method, genericUrl, content);
    httpRequest.setFollowRedirects(false);

    if (request.getConnectionTimeoutInSeconds() != null) {
      long connectionTimeout =
          TimeUnit.SECONDS.toMillis(Long.parseLong(request.getConnectionTimeoutInSeconds()));
      int intConnectionTimeout = Math.toIntExact(connectionTimeout);
      httpRequest.setConnectTimeout(intConnectionTimeout);
      httpRequest.setReadTimeout(intConnectionTimeout);
      httpRequest.setWriteTimeout(intConnectionTimeout);
    }

    return httpRequest;
  }

  protected HttpResponse sendRequest(final HttpRequest request) throws IOException {
    try {
      return request.execute();
    } catch (HttpResponseException hrex) {
      throw new ConnectorException(String.valueOf(hrex.getStatusCode()), hrex.getMessage());
    }
  }

  private HttpRequest createRequest(final HttpJsonRequest request, final String token)
      throws IOException {
    final var url = request.getUrl();
    HttpContent content = createContent(request);
    final GenericUrl genericUrl = new GenericUrl(url);
    if (request.hasQueryParameters()) {
      genericUrl.putAll(request.getQueryParameters());
    }
    final var headers = createHeaders(request, token);
    final var httpRequest = initRequest(request, url, content, genericUrl);
    httpRequest.setHeaders(headers);

    return httpRequest;
  }

  private HttpContent createContent(final HttpJsonRequest request) {
    HttpContent httpContent = null;
    if (request.hasBody()) {
      httpContent = new JsonHttpContent(gsonFactory, request.getBody());
    }
    return httpContent;
  }

  private HttpHeaders createHeaders(final HttpJsonRequest request, final String token) {
    final HttpHeaders httpHeaders = new HttpHeaders();

    if (request.hasBody()) {
      httpHeaders.setContentType(APPLICATION_JSON.getMimeType());
    }

    if (request.hasAuthentication()) {
      if (token != null && !token.isEmpty()) {
        httpHeaders.setAuthorization("Bearer " + token);
      }
      request.getAuthentication().setHeaders(httpHeaders);
    }

    if (request.hasHeaders()) {
      httpHeaders.putAll(request.getHeaders());
    }
    return httpHeaders;
  }

  private HttpJsonResult toHttpJsonResponse(final HttpResponse externalResponse) {
    final HttpJsonResult httpJsonResult = new HttpJsonResult();
    httpJsonResult.setStatus(externalResponse.getStatusCode());
    final Map<String, Object> headers = new HashMap<>();
    externalResponse
        .getHeaders()
        .forEach(
            (k, v) -> {
              if (v instanceof List && ((List<?>) v).size() == 1) {
                headers.put(k, ((List<?>) v).get(0));
              } else {
                headers.put(k, v);
              }
            });
    httpJsonResult.setHeaders(headers);
    try (InputStream content = externalResponse.getContent();
        Reader reader = new InputStreamReader(content)) {
      final Object body = gson.fromJson(reader, Object.class);
      if (body != null) {
        httpJsonResult.setBody(body);
      }
    } catch (final Exception e) {
      LOGGER.error("Failed to parse external response: {}", externalResponse, e);
    }
    return httpJsonResult;
  }
}
