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
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.Gson;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.model.HttpJsonRequest;
import io.camunda.connector.http.model.HttpJsonResult;
import io.camunda.connector.impl.ConnectorInputException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpInvocationHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpInvocationHelper.class);

  private final Gson gson;
  private final GsonFactory gsonFactory;
  private final HttpRequestFactory requestFactory;

  public HttpInvocationHelper(
      Gson gson, HttpRequestFactory requestFactory, GsonFactory gsonFactory) {
    this.gson = gson;
    this.gsonFactory = gsonFactory;
    this.requestFactory = requestFactory;
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

    //String jsonString = gson.toJson(request);
    //LOGGER.info("Sending request to proxy: " + jsonString);

    // Using the JsonHttpContent cannot work with an element on the root content??
    // final HttpContent content = new JsonHttpContent(gsonFactory, jsonString);

    // Doing it manually now
    HttpContent content =
        new AbstractHttpContent("application/json; charset=UTF-8") {
          public void writeTo(OutputStream outputStream) throws IOException {
            OutputStreamWriter writer = new OutputStreamWriter(outputStream);
            gson.toJson(request, writer);
            writer.flush();
            writer.close();
          }
        };
    final GenericUrl genericUrl = new GenericUrl(proxyUrl);

    final HttpRequest httpRequest = requestFactory.buildPostRequest(genericUrl, content);
    httpRequest.setFollowRedirects(false);

    if (request.getConnectionTimeoutInSeconds() != null) {
      long connectionTimeout =
          TimeUnit.SECONDS.toMillis(Long.parseLong(request.getConnectionTimeoutInSeconds()));
      int intConnectionTimeout = Math.toIntExact(connectionTimeout);
      httpRequest.setConnectTimeout(intConnectionTimeout);
      httpRequest.setReadTimeout(intConnectionTimeout);
      httpRequest.setWriteTimeout(intConnectionTimeout);
    }

    HttpResponse httpResponse = httpRequest.execute();
    if (!httpResponse.isSuccessStatusCode()) {
      LOGGER.debug(
          "Proxy invocation failed with HTTP error code {}: {}",
          httpResponse.getStatusCode(),
          httpResponse.getStatusMessage());
      throw new RuntimeException(
          "Failed to execute HTTP request: " + httpResponse.getStatusMessage());
    }

    try (InputStream responseContentStream = httpResponse.getContent();
        Reader reader = new InputStreamReader(responseContentStream)) {
      final HttpJsonResult jsonResult = gson.fromJson(reader, HttpJsonResult.class);

      LOGGER.info("Proxy returned result: " + jsonResult);

      return jsonResult;
    } catch (final Exception e) {
      LOGGER.debug("Failed to parse external response: {}", httpResponse, e);
      throw new ConnectorException("Failed to parse result: " + e.getMessage(), e);
    }
  }

  public HttpRequest createRequest(final HttpJsonRequest request) throws IOException {
    final String url = request.getUrl();
    // TODO: Decide if we want to keep it now that we can use a proxy
    if (url.contains("computeMetadata")) {
      throw new ConnectorInputException(new ValidationException("The provided URL is not allowed"));
    }

    final var content = createContent(request);
    final var method = request.getMethod().toUpperCase();

    final GenericUrl genericUrl = new GenericUrl(url);

    if (request.hasQueryParameters()) {
      genericUrl.putAll(request.getQueryParameters());
    }

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

    final var headers = createHeaders(request);
    httpRequest.setHeaders(headers);

    return httpRequest;
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
