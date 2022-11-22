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
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonGenerator;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.Gson;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.http.components.GsonComponentSupplier;
import io.camunda.connector.http.components.HttpTransportComponentSupplier;
import io.camunda.connector.http.model.HttpJsonRequest;
import io.camunda.connector.http.model.HttpJsonResult;
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
  public static final String PROXY_FUNCTION_URL_ENV_NAME = "CAMUNDA_CONNECTOR_HTTP_PROXY_URL";

  private final Gson gson;
  private final GsonFactory gsonFactory;
  private final HttpRequestFactory requestFactory;

  private final String proxyFunctionUrl;

  public HttpJsonFunction() {
    this(ConnectorConfigurationUtil.getProperty(PROXY_FUNCTION_URL_ENV_NAME));
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
  }

  @Override
  public Object execute(final OutboundConnectorContext context) throws IOException {
    final var json = context.getVariables();
    final var request = gson.fromJson(json, HttpJsonRequest.class);

    context.validate(request);
    context.replaceSecrets(request);

    if (proxyFunctionUrl != null) {
      return executeRequestViaProxy(proxyFunctionUrl, request);
    } else {
      return executeRequestDirectly(request);
    }
  }

  public HttpJsonResult executeRequestDirectly(HttpJsonRequest request) throws IOException {
    try {
      final HttpRequest externalRequest = createRequest(request);
      HttpResponse externalResponse = externalRequest.execute();
      return toHttpJsonResponse(externalResponse);
    } catch (HttpResponseException hrex) {
      throw new ConnectorException(String.valueOf(hrex.getStatusCode()), hrex.getMessage(), hrex);
    }
  }

  public HttpJsonResult executeRequestViaProxy(String proxyUrl, HttpJsonRequest request)
      throws IOException {
    // Using the JsonHttpContent cannot work with an element on the root content,
    // hence write it ourselves:
    HttpContent content =
        new AbstractHttpContent("application/json; charset=UTF-8") {
          public void writeTo(OutputStream outputStream) throws IOException {
            JsonGenerator jsonGenerator =
                gsonFactory.createJsonGenerator(outputStream, StandardCharsets.UTF_8);
            jsonGenerator.serialize(request);
            jsonGenerator.flush();
          }
        };
    final GenericUrl genericUrl = new GenericUrl(proxyUrl);

    final HttpRequest httpRequest = requestFactory.buildPostRequest(genericUrl, content);
    httpRequest.setFollowRedirects(false);
    setTimeout(request, httpRequest);

    HttpResponse httpResponse = httpRequest.execute();
    if (!httpResponse.isSuccessStatusCode()) {
      LOGGER.debug(
          "Proxy invocation failed with HTTP error code {}: {}",
          httpResponse.getStatusCode(),
          httpResponse.getStatusMessage());
      throw new ConnectorException(
          String.valueOf(httpResponse.getStatusCode()),
          "Failed to execute HTTP request: " + httpResponse.getStatusMessage());
    }

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

  public HttpRequest createRequest(final HttpJsonRequest request) throws IOException {
    final String method = request.getMethod().toUpperCase();
    final GenericUrl genericUrl = new GenericUrl(request.getUrl());
    final HttpContent content = createContent(request);
    final HttpHeaders headers = createHeaders(request);

    if (request.hasQueryParameters()) {
      genericUrl.putAll(request.getQueryParameters());
    }

    final var httpRequest = requestFactory.buildRequest(method, genericUrl, content);
    httpRequest.setFollowRedirects(false);
    setTimeout(request, httpRequest);
    httpRequest.setHeaders(headers);

    return httpRequest;
  }

  private void setTimeout(HttpJsonRequest request, HttpRequest httpRequest) {
    if (request.getConnectionTimeoutInSeconds() != null) {
      long connectionTimeout =
          TimeUnit.SECONDS.toMillis(Long.parseLong(request.getConnectionTimeoutInSeconds()));
      int intConnectionTimeout = Math.toIntExact(connectionTimeout);
      httpRequest.setConnectTimeout(intConnectionTimeout);
      httpRequest.setReadTimeout(intConnectionTimeout);
      httpRequest.setWriteTimeout(intConnectionTimeout);
    }
  }

  public HttpContent createContent(final HttpJsonRequest request) {
    if (request.hasBody()) {
      return new JsonHttpContent(gsonFactory, request.getBody());
    } else {
      return null;
    }
  }

  public HttpHeaders createHeaders(final HttpJsonRequest request) {
    final HttpHeaders httpHeaders = new HttpHeaders();
    if (request.hasBody()) {
      httpHeaders.setContentType(APPLICATION_JSON.getMimeType());
    }
    if (request.hasAuthentication()) {
      request.getAuthentication().setHeaders(httpHeaders);
    }
    if (request.hasHeaders()) {
      httpHeaders.putAll(request.getHeaders());
    }
    return httpHeaders;
  }

  public HttpJsonResult toHttpJsonResponse(final HttpResponse externalResponse) {
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
