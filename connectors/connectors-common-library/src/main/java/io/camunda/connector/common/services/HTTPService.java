/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.common.services;

import static org.apache.http.entity.ContentType.APPLICATION_JSON;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.gson.Gson;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.common.constants.Constants;
import io.camunda.connector.common.model.CommonRequest;
import io.camunda.connector.common.model.CommonResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HTTPService {

  private static final Logger LOGGER = LoggerFactory.getLogger(HTTPService.class);

  private final Gson gson;

  private static HTTPService instance;

  private HTTPService(final Gson gson) {
    this.gson = gson;
  }

  public static HTTPService getInstance(final Gson gson) {
    if (instance == null) {
      instance = new HTTPService(gson);
    }
    return instance;
  }

  public HttpHeaders createHeaders(final CommonRequest request, String bearerToken) {
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
    return httpHeaders;
  }

  public HttpResponse executeHttpRequest(HttpRequest externalRequest) throws IOException {
    try {
      return externalRequest.execute();
    } catch (HttpResponseException httpResponseException) {
      var errorCode = String.valueOf(httpResponseException.getStatusCode());
      var errorMessage = httpResponseException.getMessage();
      throw new ConnectorException(errorCode, errorMessage, httpResponseException);
    }
  }

  public <T extends CommonResult> T toHttpJsonResponse(
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
    try (InputStream content = externalResponse.getContent();
        Reader reader = new InputStreamReader(content)) {
      final Object body = gson.fromJson(reader, Object.class);
      if (body != null) {
        connectorResult.setBody(body);
      }
    } catch (final Exception e) {
      LOGGER.error("Failed to parse external response: {}", externalResponse, e);
    }
    return connectorResult;
  }
}
