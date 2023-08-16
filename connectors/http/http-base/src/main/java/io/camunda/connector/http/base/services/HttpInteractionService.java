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

import static org.apache.http.entity.ContentType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.base.constants.Constants;
import io.camunda.connector.http.base.model.ErrorResponse;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpInteractionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpInteractionService.class);

  private final ObjectMapper objectMapper;

  public HttpInteractionService(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public HttpHeaders createHeaders(final HttpCommonRequest request, String bearerToken) {
    final HttpHeaders httpHeaders = new HttpHeaders();
    if (Constants.POST.equalsIgnoreCase(request.getMethod())) {
      httpHeaders.setContentType(APPLICATION_JSON.getMimeType());
    }
    if (request.hasAuthentication()) {
      if (bearerToken != null && !bearerToken.isEmpty()) {
        httpHeaders.setAuthorization("Bearer " + bearerToken);
      }
      request.getAuthentication().setHeaders(httpHeaders);
    }
    httpHeaders.putAll(extractRequestHeaders(request));
    return httpHeaders;
  }

  public static HttpHeaders extractRequestHeaders(final HttpCommonRequest httpCommonRequest) {
    if (httpCommonRequest.hasHeaders()) {
      final HttpHeaders httpHeaders = new HttpHeaders();
      httpCommonRequest.getHeaders().forEach(httpHeaders::set);
      return httpHeaders;
    }

    return new HttpHeaders();
  }

  public HttpResponse executeHttpRequest(com.google.api.client.http.HttpRequest externalRequest)
      throws IOException {
    return executeHttpRequest(externalRequest, false);
  }

  public HttpResponse executeHttpRequest(
      com.google.api.client.http.HttpRequest externalRequest, boolean isProxyCall)
      throws IOException {
    try {
      return externalRequest.execute();
    } catch (HttpResponseException hrex) {
      var errorCode = String.valueOf(hrex.getStatusCode());
      var errorMessage = hrex.getMessage();
      if (isProxyCall && hrex.getContent() != null) {
        try {
          final var errorContent = objectMapper.readValue(hrex.getContent(), ErrorResponse.class);
          errorCode = errorContent.getErrorCode();
          errorMessage = errorContent.getError();
        } catch (Exception e) {
          // cannot be loaded as JSON, ignore and use plain message
          LOGGER.warn("Error response cannot be parsed as JSON! Will use the plain message.");
        }
      }
      throw new ConnectorException(errorCode, errorMessage, hrex);
    }
  }

  public <T extends HttpCommonResult> T toHttpResponse(
      final HttpResponse externalResponse, final Class<T> resultClass)
      throws InstantiationException, IllegalAccessException {
    T connectorResult = resultClass.newInstance();
    connectorResult.setStatus(externalResponse.getStatusCode());
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
    connectorResult.setHeaders(headers);
    try (InputStream content = externalResponse.getContent()) {
      String bodyString = null;
      if (content != null) {
        bodyString = new String(content.readAllBytes(), StandardCharsets.UTF_8);
      }

      if (bodyString != null) {
        if (isJSONValid(bodyString)) {
          Object body = objectMapper.readValue(bodyString, Object.class);
          connectorResult.setBody(body);
        } else {
          connectorResult.setBody(bodyString);
        }
      }
    } catch (final Exception e) {
      LOGGER.error("Failed to parse external response: {}", externalResponse, e);
    }
    return connectorResult;
  }

  protected static boolean isJSONValid(String jsonInString) {
    try (JsonReader reader = new JsonReader(new StringReader(jsonInString))) {
      final JsonElement jsonElement = JsonParser.parseReader(reader);
      return jsonElement.isJsonObject() || jsonElement.isJsonArray();
    } catch (JsonParseException | IOException e) {
      return false;
    }
  }
}
