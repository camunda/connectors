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
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.UrlEncodedContent;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.http.auth.OAuthAuthentication;
import io.camunda.connector.http.auth.ProxyOAuthHelper;
import io.camunda.connector.http.components.GsonComponentSupplier;
import io.camunda.connector.http.components.HttpTransportComponentSupplier;
import io.camunda.connector.http.constants.Constants;
import io.camunda.connector.http.model.ErrorResponse;
import io.camunda.connector.http.model.HttpJsonRequest;
import io.camunda.connector.http.model.HttpJsonResult;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.impl.config.ConnectorConfigurationUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
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

  private String proxyFunctionUrl;
  private final OAuth2Credentials proxyCredentials;

  public HttpJsonFunction() {
    this(ConnectorConfigurationUtil.getProperty(Constants.PROXY_FUNCTION_URL_ENV_NAME));
  }

  public HttpJsonFunction(String proxyFunctionUrl) {
    this(
        GsonComponentSupplier.gsonInstance(),
        HttpTransportComponentSupplier.httpRequestFactoryInstance(),
        GsonComponentSupplier.gsonFactoryInstance(),
        proxyFunctionUrl);
  }

  public HttpJsonFunction(
      final Gson gson,
      final HttpRequestFactory requestFactory,
      final GsonFactory gsonFactory,
      String proxyFunctionUrl) {
    this.gson = gson;
    this.requestFactory = requestFactory;
    this.gsonFactory = gsonFactory;
    this.proxyFunctionUrl = proxyFunctionUrl;
    this.proxyCredentials = ProxyOAuthHelper.initializeCredentials(proxyFunctionUrl);
  }

  @Override
  public Object execute(final OutboundConnectorContext context) throws IOException {
    final var json = context.getVariables();
    final var request = gson.fromJson(json, HttpJsonRequest.class);

    context.validate(request);
    context.replaceSecrets(request);

    proxyFunctionUrl = null;
    if (proxyFunctionUrl != null) {
      return executeRequestViaProxy(request);
    } else {
      return executeRequestDirectly(request);
    }
  }

  protected HttpJsonResult executeRequestDirectly(HttpJsonRequest request) throws IOException {

    String bearerToken = null;
    if (request.getAuthentication() != null
        && request.getAuthentication() instanceof OAuthAuthentication) {
      final HttpRequest oauthRequest = createOAuthRequest(request);
      final HttpResponse oauthResponse = executeHttpRequest(oauthRequest, false);
      bearerToken = extractAccessToken(oauthResponse);
    }

    final HttpRequest httpRequest = createRequest(request, bearerToken);
    HttpResponse httpResponse = executeHttpRequest(httpRequest, false);
    return toHttpJsonResponse(httpResponse);
  }

  protected String extractAccessToken(HttpResponse oauthResponse) throws IOException {
    String oauthResponseStr = oauthResponse.parseAsString();
    if (oauthResponseStr != null && !oauthResponseStr.isEmpty()) {
      JsonObject jsonObject = gson.fromJson(oauthResponseStr, JsonObject.class);
      if (jsonObject.get(Constants.ACCESS_TOKEN) != null) {
        return jsonObject.get(Constants.ACCESS_TOKEN).getAsString();
      }
    }
    return null;
  }

  protected HttpRequest createOAuthRequest(HttpJsonRequest request) throws IOException {
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
    // Using the JsonHttpContent cannot work with an element on the root content,
    // hence write it ourselves:
    final String contentAsJson = gson.toJson(request);
    HttpContent content =
        new AbstractHttpContent(Constants.APPLICATION_JSON_CHARSET_UTF_8) {
          public void writeTo(OutputStream outputStream) throws IOException {
            outputStream.write(contentAsJson.getBytes(StandardCharsets.UTF_8));
          }
        };
    final GenericUrl genericUrl = new GenericUrl(proxyFunctionUrl);

    final HttpRequest httpRequest = requestFactory.buildPostRequest(genericUrl, content);
    httpRequest.setFollowRedirects(false);
    setTimeout(request, httpRequest);
    ProxyOAuthHelper.addOauthHeaders(httpRequest, proxyCredentials);

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

  protected HttpRequest createRequest(final HttpJsonRequest request, String bearerToken)
      throws IOException {
    // TODO: add more holistic solution
    if (request.getUrl().contains("computeMetadata")) {
      throw new ConnectorInputException(new ValidationException("The provided URL is not allowed"));
    }
    final String method = request.getMethod().toUpperCase();
    final GenericUrl genericUrl = new GenericUrl(request.getUrl());
    final HttpContent content = createContent(request);
    final HttpHeaders headers = createHeaders(request, bearerToken);

    if (request.hasQueryParameters()) {
      genericUrl.putAll(request.getQueryParameters());
    }

    final var httpRequest = requestFactory.buildRequest(method, genericUrl, content);
    httpRequest.setFollowRedirects(false);
    setTimeout(request, httpRequest);
    httpRequest.setHeaders(headers);

    return httpRequest;
  }

  protected void setTimeout(HttpJsonRequest request, HttpRequest httpRequest) {
    if (request.getConnectionTimeoutInSeconds() != null) {
      long connectionTimeout =
          TimeUnit.SECONDS.toMillis(Long.parseLong(request.getConnectionTimeoutInSeconds()));
      int intConnectionTimeout = Math.toIntExact(connectionTimeout);
      httpRequest.setConnectTimeout(intConnectionTimeout);
      httpRequest.setReadTimeout(intConnectionTimeout);
      httpRequest.setWriteTimeout(intConnectionTimeout);
    }
  }

  protected HttpContent createContent(final HttpJsonRequest request) {
    if (request.hasBody()) {
      return new JsonHttpContent(gsonFactory, request.getBody());
    } else {
      return null;
    }
  }

  protected HttpHeaders createHeaders(final HttpJsonRequest request, String bearerToken) {
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

  protected HttpJsonResult toHttpJsonResponse(final HttpResponse externalResponse) {
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
